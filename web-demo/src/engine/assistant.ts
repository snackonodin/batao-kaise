// The core voice workflow: tap mic -> "Batao, kya karna hai?" -> speak -> read-back -> Haan -> do it, step by step.
// Three skills of the same workflow: udhaar, bill (read + guided Demo Pay), and "atak gaya" (Rescue Circle).
// Port of app/.../core/Assistant.kt + Tasks + JourneyRunner. No DOM, mic or speaker here: the UI does I/O.
import { Normalized, digitGroups, findNumbers, normalize } from "./lang";
import { Intent, caption, describe, detect, extractName, readBack } from "./intent";
import { BillField, FieldKind, FIELD_LABEL, OcrLine, parseBill, position, spokenDate } from "./bill";
import { KV, Ledger, pickName, resolveName, upiUri } from "./ledger";
import { redact } from "./redact";

export type Phase = "idle" | "listening" | "processing" | "speaking" | "guiding";
export type PayScreen = "home" | "bills" | "electricity" | "pay" | "pin" | "success" | "other";
export type Picture = "bill" | "udhaar" | "madad";
export type ShareTarget = "whatsapp" | "send" | "ai" | "cancel";

export type View =
  | { kind: "home" }
  | { kind: "bill"; loading: boolean; image: string; width: number; height: number; fields: BillField[]; ocrNote: string }
  | { kind: "pay"; screen: PayScreen; highlight: string | null; account: string; accountTyped: string; amount: number; accountOk: boolean; pinLength: number; sensitive: boolean }
  | { kind: "ledger"; balances: { name: string; balance: number }[]; highlight: string | null; lastEntryId: number | null }
  | { kind: "reminder"; name: string; amount: number; text: string; upi: string; stage: "draft" | "chat" | "sent"; highlight: string | null }
  | { kind: "rescue"; summary: string; removed: Record<string, number>; to: string; stage: "review" | "share" | "chat" | "sent" | "ai"; highlight: string | null; aiQuestion: string };

export interface Say { id: number; text: string; slow: boolean }

export interface State {
  phase: Phase;
  /** Big Hinglish text in the bubble. */
  bubble: string;
  /** Live transcript. */
  live: string;
  /** True = UI should start speech recognition now (and cancel any speech). */
  listen: boolean;
  /** Line to speak. Speak it, then call spoken(id). */
  say: Say | null;
  showYesNo: boolean;
  showPictures: boolean;
  showDone: boolean;
  /** True = UI should load the sample bill OCR and call billOcr(). */
  needBill: boolean;
  view: View;
  /** English caption for judges. */
  caption: string;
  /** Last detected intent, for the debug panel. */
  lastIntent: string;
}

export interface Options { store?: KV; upiId?: string; shopName?: string; trustedName?: string }

type Handler = (n: Normalized, i: Intent) => void;

const DEMO_ACCOUNT = "451277883301";
const INITIAL: State = {
  phase: "idle", bubble: "Mic dabaiye aur boliye", live: "", listen: false, say: null, showYesNo: false,
  showPictures: false, showDone: false, needBill: false, view: { kind: "home" }, caption: "Tap the mic and speak in Hindi or Hinglish.", lastIntent: "",
};

export class Assistant {
  state: State = { ...INITIAL };
  readonly ledger: Ledger;
  readonly cases: { at: number; summary: string; sharedTo: string }[] = [];
  private subs = new Set<(s: State) => void>();
  private handler: Handler | null = null;
  /** Handler of the last question, for re-asking it. */
  private lastHandler: Handler | null = null;
  /** Last question wants a short answer (a name, an amount): one word is not noise. */
  private shortOk = false;
  private afterSpeak: (() => void) | null = null;
  private sayId = 0;
  private lastQuestion = "";
  private lastYesNo = false;
  private noiseCount = 0;
  private silenceCount = 0;
  private noCount = 0;
  private guiding = false;
  private payFails: Record<string, number> = {};
  private rescueOffered = false;
  private lastStep = "";
  /** Last task the user started, for the Rescue Circle summary. */
  private lastGoal: string | null = null;
  private bill: { account: string | null; amount: number | null } = { account: null, amount: null };
  private screenStack: PayScreen[] = [];
  private upiId: string; private shop: string; private trusted: string;

  constructor(o: Options = {}) {
    this.ledger = new Ledger(o.store);
    this.upiId = o.upiId ?? "sunita.kirana@demoupi";
    this.shop = o.shopName ?? "Sunita Kirana Store";
    this.trusted = o.trustedName ?? "Beta (Rahul)";
  }

  // ---------- plumbing ----------
  subscribe(f: (s: State) => void) { this.subs.add(f); f(this.state); return () => { this.subs.delete(f); }; }
  private set(p: Partial<State>) { this.state = { ...this.state, ...p }; this.subs.forEach((f) => f(this.state)); }

  private speak(text: string, then: (() => void) | null = null, slow = false) {
    this.afterSpeak = then;
    this.set({ say: { id: ++this.sayId, text, slow }, bubble: text, phase: "speaking", listen: false });
  }
  /** UI: the utterance with this id finished (or TTS is unavailable). */
  spoken(id: number) {
    if (this.state.say?.id !== id) return;
    const then = this.afterSpeak; this.afterSpeak = null;
    this.set({ say: null, phase: this.guiding ? "guiding" : "idle" });
    then?.();
  }

  private ask(line: string, yesNo: boolean, h: Handler, shortOk = false) {
    this.lastQuestion = line; this.lastYesNo = yesNo; this.handler = h; this.lastHandler = h; this.shortOk = shortOk;
    this.set({ showYesNo: yesNo, live: "" });
    this.speak(line, () => { if (this.handler === h) this.listenNow(); });
  }
  private askYesNo(line: string, onYes: () => void, onNo: () => void) {
    this.ask(line, true, (_n, i) => {
      if (i.kind === "yes") { this.set({ showYesNo: false }); onYes(); }
      else if (i.kind === "no") { this.set({ showYesNo: false }); onNo(); }
      else this.reask("Haan ya nahi boliye.");
    });
  }
  private reask(line: string) { this.ask(line, this.lastYesNo, this.lastHandler ?? ((n, i) => this.onTask(n, i)), this.shortOk); }
  private listenNow() { this.set({ listen: true, phase: "listening", live: "", say: null }); }

  // ---------- UI inputs ----------
  micTapped() {
    if (this.state.phase === "listening") { this.set({ listen: false, phase: this.guiding ? "guiding" : "idle" }); return; }
    if (this.state.say || this.handler) { this.afterSpeak = null; if (!this.handler) this.handler = (n, i) => this.guideAnswer(n, i); this.listenNow(); return; }
    if (this.guiding) { this.handler = (n, i) => this.guideAnswer(n, i); this.listenNow(); return; }
    this.startTask();
  }
  startTask() {
    this.noiseCount = 0; this.silenceCount = 0; this.noCount = 0;
    this.guiding = false;
    this.set({ showPictures: false, showYesNo: false, showDone: false, needBill: false, live: "", view: { kind: "home" }, caption: "Tap the mic and speak in Hindi or Hinglish." });
    this.ask("Batao, kya karna hai?", false, (n, i) => this.onTask(n, i));
  }
  partial(text: string) { this.set({ live: text }); }
  /** Final transcript (speech, or the "type instead" box). */
  heard(text: string) {
    this.set({ listen: false, phase: "processing", live: text });
    if (!this.handler) this.handler = this.guiding ? (n, i) => this.guideAnswer(n, i) : (n, i) => this.onTask(n, i);
    const n = normalize(text);
    const i = detect(n);
    this.set({ lastIntent: describe(i) });
    if (this.global(i)) return;
    if (i.kind === "noise" && !this.lastYesNo && !this.shortOk) { this.onNoise(); return; }
    this.noiseCount = 0; this.silenceCount = 0;
    const h = this.handler!; this.handler = null;   // each answer is consumed once
    h(n, i);
  }
  silence() {
    this.silenceCount++;
    if (this.silenceCount === 1) this.speak("Main sun raha hoon.", () => this.listenNow());
    else { this.silenceCount = 0; this.set({ listen: false, phase: this.guiding ? "guiding" : "idle", bubble: "Jab taiyaar hon, mic dabaiye." }); }
  }
  noMatch() { this.onNoise(); }
  yes() { this.inject({ kind: "yes" }); }
  no() { this.inject({ kind: "no" }); }
  done() { this.inject({ kind: "cmd", command: "done" }); }
  picture(p: Picture) {
    this.set({ showPictures: false });
    this.confirm(p === "bill" ? { kind: "bill_pay" } : p === "udhaar" ? { kind: "udhaar_balance" } : { kind: "stuck" });
  }
  reset() {
    this.handler = null; this.afterSpeak = null; this.guiding = false;
    this.set({ ...INITIAL });
  }

  private inject(i: Intent) {
    this.set({ listen: false });
    if (this.global(i)) return;
    const h = this.handler ?? (this.guiding ? (n: Normalized, x: Intent) => this.guideAnswer(n, x) : null);
    this.handler = null;
    h?.(normalize(i.kind), i);
  }

  private global(i: Intent): boolean {
    if (i.kind !== "cmd") return false;
    switch (i.command) {
      case "stop": this.reset(); this.speak("Theek hai, ruk gaya."); return true;
      case "repeat": { const last = this.state.bubble; const h = this.handler; this.speak(last, () => { if (h) this.listenNow(); }, true); return true; }
      case "back":
        if (this.guiding && this.state.view.kind === "pay") { this.speak("Upar 'Back' dabaiye."); this.set({ view: { ...this.state.view, highlight: "back_btn" } }); return true; }
        this.reask(this.lastQuestion || "Batao, kya karna hai?"); return true;
      case "new_task": this.reset(); this.startTask(); return true;
      case "done": if (this.guiding) { this.speak("Abhi ye baaki hai: " + this.lastStep); return true; } return false;
    }
  }

  private onNoise() {
    this.noiseCount++;
    if (this.noiseCount >= 2) {
      this.noiseCount = 0;
      if (this.lastQuestion.startsWith("Batao")) this.showPictures(); else this.reask(this.lastQuestion);
    } else {
      const h = this.handler;
      this.set({ caption: "Too noisy or too short: it asks again instead of guessing." });
      this.speak("Thoda shor hai. Phone munh ke paas laakar dobara boliye.", () => { if (this.handler === h) this.listenNow(); });
    }
  }
  private showPictures() {
    this.handler = (n, i) => this.onTask(n, i);
    this.set({ showPictures: true, showYesNo: false, caption: "After two failures it shows picture buttons." });
    this.speak("Neeche tasveer dabaiye: Bill, Udhaar ya Phone madad.");
  }

  // ---------- understanding ----------
  private onTask(n: Normalized, i: Intent) {
    switch (i.kind) {
      case "noise": return this.onNoise();
      case "unknown":
        this.handler = null;
        this.set({ caption: "Unknown request: it says so and offers human help (Rescue Circle)." });
        return this.speak("Ye main abhi nahi seekha. Kisi apne se madad maangein?", () => this.rescue(`unknown: "${n.raw}"`));
      case "yes": case "no": return this.reask("Batao, kya karna hai?");
      case "udhaar_add": case "udhaar_repay":
        return this.fillUdhaar(i.name, i.amount, (name, amount) => this.confirm({ ...i, name, amount }));
      case "udhaar_remind":
        return i.name ? this.withName(i.name, (name) => this.confirm({ kind: "udhaar_remind", name }), false)
          : this.askName((nm) => this.withName(nm, (name) => this.confirm({ kind: "udhaar_remind", name }), false));
      default: return this.confirm(i);
    }
  }

  private fillUdhaar(name: string | null, amount: number | null, done: (n: string, a: number) => void) {
    if (!name) return this.askName((nm) => this.fillUdhaar(nm, amount, done));
    if (amount == null) return this.askAmount((a) => this.fillUdhaar(name, a, done));
    this.withName(name, (full) => done(full, amount));
  }
  private askName(on: (n: string) => void) {
    this.ask("Sirf naam boliye.", false, (n) => {
      const name = extractName(n);
      if (!name) this.reask("Naam samajh nahi aaya. Sirf naam boliye."); else on(name);
    }, true);
  }
  private askAmount(on: (a: number) => void) {
    this.ask("Kitne rupaye? Sirf rakam boliye.", false, (n) => {
      const a = findNumbers(n.canon).find((x) => x > 0);
      if (a == null) this.reask("Rakam samajh nahi aayi. Jaise: do sau."); else on(a);
    }, true);
  }
  /** Similar names: "Ramesh Kumar ya Ramesh Yadav?" before the read-back. */
  private withName(spoken: string, on: (full: string) => void, allowNew = true) {
    const r = resolveName(spoken, this.ledger.names());
    if (r.kind === "exact") return on(r.name);
    if (r.kind === "new") {
      if (!allowNew) { this.handler = null; return this.speak(`${r.name} naam khaate mein nahi hai.`); }
      this.set({ caption: "A name not in the notebook: it checks before adding a new customer." });
      return this.askYesNo(`${r.name} naya naam hai. Likh doon?`, () => on(r.name),
        () => this.askName((nm) => this.withName(nm, on, allowNew)));
    }
    const q = r.options.join(" ya ") + "?";
    this.set({ caption: "Similar names in the ledger: it asks which one." });
    this.ask(q, false, (n) => {
      const pick = pickName(n.tokens, r.options) ?? (() => {
        const nm = extractName(n); if (!nm) return null;
        const again = resolveName(nm.includes(" ") ? nm : `${spoken.split(" ")[0]} ${nm}`, r.options);
        return again.kind === "exact" ? again.name : null;
      })();
      if (pick) on(pick); else this.reask(q);
    }, true);
  }

  /** Read back and wait for Haan. Nothing happens before Haan. Amounts over 2,000 get a second check. */
  private confirm(i: Intent) {
    const line = readBack(i);
    if (!line) return this.run(i);
    this.set({ caption: caption(i) + " It reads back first; nothing happens before \"Haan\"." });
    this.ask(line, true, (_n, ans) => {
      if (ans.kind === "yes") {
        this.set({ showYesNo: false });
        const amt = i.kind === "udhaar_add" || i.kind === "udhaar_repay" ? i.amount : null;
        if (amt != null && amt > 2000) this.askYesNo("Do hazaar se zyada hai. Pakka?", () => this.run(i), () => this.afterNo());
        else this.run(i);
      } else if (ans.kind === "no") this.afterNo();
      else if (ans.kind !== "noise" && ans.kind !== "unknown") this.onTask(normalize(""), ans);
      else this.reask("Haan ya nahi boliye. " + line);
    });
  }
  private afterNo() {
    this.noCount++;
    this.set({ showYesNo: false, caption: "Misheard? Say \"Nahi\" and it asks again." });
    if (this.noCount >= 2) { this.noCount = 0; this.showPictures(); }
    else this.ask("Achha, phir se bataiye.", false, (n, i) => this.onTask(n, i));
  }

  // ---------- doing ----------
  private run(i: Intent) {
    this.handler = null;
    const goals: Partial<Record<Intent["kind"], string>> = {
      bill_pay: "Bijli ka bill bharna", udhaar_add: "Udhaar likhna", udhaar_repay: "Udhaar ki wapsi likhna",
      udhaar_balance: "Udhaar ka hisaab sunna", udhaar_remind: "WhatsApp par udhaar ki yaad dilana",
    };
    if (goals[i.kind]) this.lastGoal = goals[i.kind]!;
    switch (i.kind) {
      case "bill_pay": return this.startBill();
      case "udhaar_add": case "udhaar_repay": {
        const e = this.ledger.add(i.name!, i.kind === "udhaar_add" ? i.amount! : -i.amount!, i.kind === "udhaar_add" ? "udhaar" : "wapas diye");
        const bal = this.ledger.balanceOf(i.name!);
        this.set({ view: { kind: "ledger", balances: this.ledger.balances(), highlight: i.name!, lastEntryId: e.id }, caption: "Saved to the udhaar notebook (on the phone, encrypted on Android)." });
        return this.speak(i.kind === "udhaar_add" ? `Likh diya. ${i.name} ka kul baaki ${bal} rupaye.` : `Likh liya. ${i.name} ka baaki ab ${bal} rupaye.`);
      }
      case "udhaar_balance": {
        const top = this.ledger.top(5);
        this.set({ view: { kind: "ledger", balances: this.ledger.balances(), highlight: null, lastEntryId: null }, caption: "Reads out the top five balances." });
        if (!top.length) return this.speak("Kisi ka koi baaki nahi hai.");
        const lines = top.map((b) => `${b.name}: ${b.balance} rupaye.`);
        const chain = (k: number) => k < lines.length ? this.speak(lines[k], () => chain(k + 1)) : undefined;
        return this.speak(`Sabse zyada baaki: ${lines[0]}`, () => chain(1));
      }
      case "udhaar_remind": return this.remind(i.name!);
      case "stuck": return this.rescue("atak gaya");
      default: return this.startTask();
    }
  }

  undoLast() {
    const v = this.state.view;
    if (v.kind !== "ledger" || v.lastEntryId == null) return;
    this.ledger.undo(v.lastEntryId);
    this.set({ view: { ...v, balances: this.ledger.balances(), lastEntryId: null } });
    this.speak("Hata diya.");
  }

  // ---------- udhaar reminder ----------
  private remind(name: string) {
    const bal = this.ledger.balanceOf(name);
    if (bal <= 0) return this.speak(`${name} ka koi baaki nahi hai.`);
    this.askYesNo(`${name} ke ${bal} rupaye baaki hain. WhatsApp par yaad dilaun?`, () => {
      const text = `Namaste ${name} ji, ${this.shop} se yaad dilana tha: aapke ${bal} rupaye baaki hain. Jab ho sake, is QR se UPI kar dijiye. Dhanyavaad!`;
      this.set({
        view: { kind: "reminder", name, amount: bal, text, upi: upiUri(this.upiId, this.shop, bal, "Udhaar"), stage: "draft", highlight: "share_whatsapp" },
        caption: "A polite reminder with a UPI QR. The user sends it themselves.",
      });
      this.speak("Sandesh taiyaar hai. 'WhatsApp' dabaiye.");
    }, () => this.speak("Theek hai. Jab chahein, mic dabaiye."));
  }

  /** UI: user tapped in the share sheet / chat. The app never sends by itself. */
  share(t: ShareTarget) {
    const v = this.state.view;
    if (v.kind === "reminder") {
      if (t === "cancel") { this.set({ view: { kind: "home" } }); return this.speak("Theek hai, nahi bheja."); }
      if (v.stage === "draft" && t === "whatsapp") { this.set({ view: { ...v, stage: "chat", highlight: "send" } }); return this.speak("Hare 'Send' button ko dabaiye."); }
      if (v.stage === "chat" && t === "send") { this.set({ view: { ...v, stage: "sent", highlight: null } }); return this.speak(`Bhej diya! ${v.name} ko yaad dila di.`); }
    }
    if (v.kind === "rescue") {
      if (t === "cancel") { this.set({ view: { kind: "home" } }); return this.speak("Theek hai, nahi bheja."); }
      if (t === "ai") {
        const q = `I am helping a family member use their phone. ${v.summary}\nWhat exactly should they tap next? Answer in simple Hindi, one step at a time.`;
        this.set({ view: { ...v, stage: "ai", aiQuestion: redact(q).text, highlight: null } });
        return this.speak("Sawaal taiyaar hai. Gemini ya Claude mein chipka dijiye.");
      }
      if (v.stage === "share" && t === "whatsapp") { this.set({ view: { ...v, stage: "chat", highlight: "send" } }); return this.speak("Hare 'Send' button ko dabaiye."); }
      if (v.stage === "chat" && t === "send") {
        this.cases.push({ at: Date.now(), summary: v.summary, sharedTo: v.to });
        this.set({ view: { ...v, stage: "sent", highlight: null }, caption: "Sent. The case is saved so a person can turn the fix into a reviewed journey." });
        return this.speak(`Bhej diya. ${v.to} jaldi madad karenge.`);
      }
    }
  }

  // ---------- Rescue Circle ----------
  private rescue(reason: string) {
    const inPay = this.guiding && this.state.view.kind === "pay";
    const screenName: Record<PayScreen, string> = { home: "Home", bills: "Bills", electricity: "Electricity", pay: "Pay", pin: "UPI PIN", success: "Success", other: "galat screen" };
    const lines = ["Madad chahiye (Batao Kaise se)"];
    if (inPay) {
      const v = this.state.view as Extract<View, { kind: "pay" }>;
      const fails = Object.values(this.payFails).reduce((a, b) => a + b, 0);
      lines.push("Kaam: Bijli ka bill bharna", "App: Demo Pay", `Screen: ${screenName[v.screen]}`, `Kahan atke: ${v.account ? this.lastStep.split(digitGroups(v.account)).join(v.account) : this.lastStep}`,
        `Account: ${v.account}`, `Koshish: ${fails + 1} baar`);
      lines.push(`Kaaran: ${reason}`);
    } else if (reason.startsWith("unknown:")) {
      lines.push(`Unhone poocha: ${reason.slice(8).trim()}`, "Kaaran: Batao Kaise ye kaam abhi nahi jaanta");
    } else {
      lines.push(`Kaam: ${this.lastGoal ?? "Phone par koi kaam (shuru nahi hua)"}`, `Kaaran: unhone kaha "${reason}"`);
    }
    const r = redact(lines.join("\n"));
    this.guiding = false; this.handler = null;
    this.set({
      view: { kind: "rescue", summary: r.text, removed: r.removed, to: this.trusted, stage: "review", highlight: null, aiQuestion: "" },
      showDone: false, phase: "idle",
      caption: "Rescue Circle: a summary built from the task state (not a screenshot). OTPs, PINs, account, Aadhaar, phone and UPI numbers are removed.",
    });
    const hidden = Object.values(r.removed).reduce((a, b) => a + b, 0);
    this.speak(`Ye sandesh ${this.trusted} ko jayega. ${hidden ? "Number chhupa diye hain." : ""}`.trim(), () =>
      this.askYesNo("Bhej doon?", () => {
        const v = this.state.view as Extract<View, { kind: "rescue" }>;
        this.set({ view: { ...v, stage: "share", highlight: "share_whatsapp" } });
        this.speak("'WhatsApp' dabaiye.");
      }, () => this.speak("Theek hai. Aap sandesh badal bhi sakte hain.")));
  }

  /** UI: user edited the summary. Redact again after every edit. */
  editRescue(text: string) {
    const v = this.state.view; if (v.kind !== "rescue") return;
    const r = redact(text);
    this.set({ view: { ...v, summary: r.text, removed: { ...r.removed } } });
  }

  // ---------- Bill: read aloud, then guide Demo Pay ----------
  private startBill() {
    this.set({ needBill: true, view: { kind: "bill", loading: true, image: "/sample_bill.png", width: 1080, height: 1440, fields: [], ocrNote: "Web demo: OCR result precomputed for the sample bill. Android runs ML Kit on the phone." } });
    this.speak("Bill dekh raha hoon.");
  }

  /** UI: OCR lines of the bill image (precomputed in the web demo). */
  billOcr(lines: OcrLine[], width: number, height: number, image = "/sample_bill.png") {
    const f = parseBill(lines);
    const fields = (["account", "amount", "due"] as FieldKind[]).map((k) => f[k]);
    this.bill = { account: f.account.value, amount: f.amount.value ? Math.round(parseFloat(f.amount.value.replace(/,/g, ""))) : null };
    this.set({ needBill: false, view: { kind: "bill", loading: false, image, width, height, fields, ocrNote: (this.state.view as any).ocrNote ?? "" },
      caption: "Reads each field aloud: numbers digit by digit. Unclear fields are marked \"Saaf nahi\", never guessed." });
    const spoken = fields.map((x) => this.fieldLine(x, width, height));
    const chain = (k: number): void => {
      if (k >= spoken.length) return this.askYesNo("Bharne ka tarika bataun?", () => this.startPay(), () => this.speak("Theek hai. Jab chahein, mic dabaiye."));
      this.speak(spoken[k], () => chain(k + 1));
    };
    chain(0);
  }
  billTap(kind: FieldKind) {
    const v = this.state.view; if (v.kind !== "bill") return;
    const f = v.fields.find((x) => x.kind === kind); if (f) this.speak(this.fieldLine(f, v.width, v.height));
  }
  private fieldLine(f: BillField, w: number, h: number): string {
    const pos = f.box ? position(f.box, w, h) : "";
    if (!f.value) return `${FIELD_LABEL[f.kind]} saaf nahi dikh raha.`;
    if (f.kind === "account") return `${pos} aapka account number hai: ${digitGroups(f.value)}.`.trim();
    if (f.kind === "amount") return `${pos} rakam hai: ${f.value.replace(/,/g, "")} rupaye.`.trim();
    return `${pos} aakhri tareekh hai: ${spokenDate(f.value)}.`.trim();
  }

  private startPay() {
    this.guiding = true; this.payFails = {}; this.rescueOffered = false; this.screenStack = ["home"];
    this.set({
      showDone: true, phase: "guiding",
      view: { kind: "pay", screen: "home", highlight: null, account: this.bill.account ?? "", accountTyped: "", amount: this.bill.amount ?? 0, accountOk: false, pinLength: 0, sensitive: false },
      caption: "Opens Demo Pay and highlights the exact button. It never taps for the user.",
    });
    this.guideStep();
  }

  /** What to highlight and say for the current Demo Pay screen (the journey). */
  private guideStep() {
    const v = this.state.view; if (v.kind !== "pay") return;
    const acct = v.account ? digitGroups(v.account) : "bill wala number";
    const step: Record<PayScreen, [string | null, string, boolean]> = {
      home: ["bills_tile", "Neeche 'Bills' dabaiye.", false],
      bills: ["electricity_tile", "Ab 'Electricity' dabaiye.", false],
      electricity: v.accountOk ? ["continue_btn", `Sunita Devi, ${v.amount} rupaye. Ab 'Continue' dabaiye.`, false]
        : ["account_field", `Is dabbe mein account number likhiye: ${acct}.`, false],
      pay: ["pay_btn", `Rakam ${v.amount} rupaye hai. Sahi ho to 'Pay' dabaiye.`, false],
      pin: [null, "Ye aap khud daaliye. PIN kisi ko mat bataiye.", true],
      success: [null, "Ho gaya! Bill bhar diya. Aapne khud kiya, shabaash!", false],
      other: ["back_btn", "Ye galat screen hai, peeche jaaiye.", false],
    };
    const [hl, line, sensitive] = step[v.screen];
    if (v.screen !== "other") this.lastStep = line;
    this.set({
      view: { ...v, highlight: hl, sensitive },
      caption: sensitive ? "On PIN and OTP screens the guide hides: the user types secrets alone." :
        v.screen === "other" ? "Wrong screen: it points back instead of tapping." :
          v.screen === "success" ? "Done by the user, on their own phone." : this.state.caption,
    });
    if (v.screen === "success") return this.speak(line, () => { this.guiding = false; this.set({ showDone: false, phase: "idle" }); });
    this.speak(line);
  }

  private bumpFail(key: string) {
    this.payFails[key] = (this.payFails[key] ?? 0) + 1;
    if (this.payFails[key] >= 2 && !this.rescueOffered) {
      this.rescueOffered = true;
      this.askYesNo("Lagta hai mushkil ho rahi hai. Kisi apne se madad lein?", () => { this.guiding = true; this.rescue("ek kadam do baar galat hua"); },
        () => { this.handler = null; this.guideStep(); });
      return true;
    }
    return false;
  }

  /** UI: the user tapped an element in mock Demo Pay. Demo Pay reacts like the real dummy app; the guide follows. */
  payTap(id: string) {
    const v = this.state.view; if (v.kind !== "pay") return;
    const go = (s: PayScreen) => { this.screenStack.push(s); this.set({ view: { ...v, screen: s } }); this.guideStep(); };
    if (id === "back_btn") {
      if (this.screenStack.length > 1) this.screenStack.pop();
      this.set({ view: { ...v, screen: this.screenStack[this.screenStack.length - 1] } });
      return this.guideStep();
    }
    switch (v.screen) {
      case "home": if (id === "bills_tile") return go("bills"); break;
      case "bills": if (id === "electricity_tile") return go("electricity"); break;
      case "electricity": if (id === "continue_btn" && v.accountOk) return go("pay"); if (id === "account_field") return; break;
      case "pay": if (id === "pay_btn") return go("pin"); break;
      case "pin": if (id === "pin_submit" && v.pinLength === 4) return go("success"); if (id === "pin_field") return; break;
      case "success": if (id === "home_btn") { this.guiding = false; this.set({ view: { kind: "home" }, showDone: false, phase: "idle" }); } return;
      case "other": return;
    }
    // A tap on something else: on tile screens it opens the wrong screen.
    if (v.screen === "home" || v.screen === "bills") { if (!this.bumpFail(v.screen)) go("other"); else { this.screenStack.push("other"); this.set({ view: { ...v, screen: "other", highlight: "back_btn" } }); } }
  }

  /** UI: the user typed in mock Demo Pay. The PIN's digits are never stored: only its length. */
  payType(id: string, value: string) {
    const v = this.state.view; if (v.kind !== "pay") return;
    if (id === "pin_field") { this.set({ view: { ...v, pinLength: value.replace(/\D/g, "").length } }); return; }
    if (id !== "account_field") return;
    const d = value.replace(/\D/g, "").slice(0, 12);
    const ok = d === (v.account || DEMO_ACCOUNT);
    this.set({ view: { ...v, accountTyped: d, accountOk: ok } });
    if (ok) this.guideStep();
    else if (d.length === 12) { if (!this.bumpFail("account")) this.speak(`Ye number bill se alag hai. Dhyan se: ${digitGroups(v.account || DEMO_ACCOUNT)}.`); }
  }

  private guideAnswer(n: Normalized, i: Intent) {
    this.handler = null;
    if (i.kind === "stuck") return this.confirm(i);
    if (i.kind === "unknown" || i.kind === "noise") return this.speak(this.lastStep);
    this.onTask(n, i);
  }
}

export function createAssistant(o?: Options) { return new Assistant(o); }
