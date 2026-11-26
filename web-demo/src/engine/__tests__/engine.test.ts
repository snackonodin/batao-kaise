import { describe, it, expect } from "vitest";
import { runPhrases } from "../phrases";
import { parseNumber, normalize, toLatin } from "../lang";
import { redact } from "../redact";
import { parseBill, OcrLine } from "../bill";
import { createAssistant, Assistant } from "../assistant";

describe("language", () => {
  it("numbers", () => {
    const cases: [string, number][] = [["do sau", 200], ["dhai sau", 250], ["saade teen sau", 350], ["do hazaar paanch sau", 2500],
      ["two hundred and fifty", 250], ["fifteen hundred", 1500], ["₹1,240", 1240], ["दो सौ", 200], ["ढाई सौ", 250], ["paune do sau", 175]];
    for (const [s, n] of cases) expect(parseNumber(s), s).toBe(n);
  });
  it("transliteration", () => {
    expect(toLatin("रमेश को दो सौ उधार")).toBe("ramesh ko do sau udhaar");
    expect(toLatin("बिजली")).toBe("bijli");
    expect(normalize("light ka bil").canon).toEqual(["bijli", "ka", "bill"]);
  });
  it("all 20 debug phrases pass", () => {
    const bad = runPhrases().filter((r) => !r.pass).map((r) => `${r.text} -> ${r.got} (want ${r.expect})`);
    expect(bad).toEqual([]);
  });
});

describe("redactor (fake numbers only)", () => {
  const r = (s: string) => redact(s).text;
  it("removes secrets and ids", () => {
    expect(r("OTP 482913 aaya")).toBe("OTP [OTP HATAYA] aaya");
    expect(r("UPI PIN: 1234")).toBe("UPI PIN: [PIN HATAYA]");
    expect(r("call +91 98765 43210")).toBe("call [PHONE HATAYA]");
    expect(r("pay to sunita.kirana@demoupi")).toBe("pay to [UPI HATAYA]");
    expect(r("Aadhaar 1234 5678 9012")).not.toContain("9012");
    expect(r("account 4-5-1-2-7-7-8-8-3-3-0-1")).not.toContain("3-0-1");
  });
  it("keeps amounts and dates; idempotent", () => {
    expect(r("Rakam 1240 rupaye, 15/12/2026")).toBe("Rakam 1240 rupaye, 15/12/2026");
    const once = r("number 9123456780, OTP 112233");
    expect(r(once)).toBe(once);
  });
});

const BILL: OcrLine[] = [
  { text: "DEMO BILL, NOT REAL", l: 300, t: 40, r: 780, b: 90 },
  { text: "Consumer No / उपभोक्ता संख्या", l: 600, t: 220, r: 1020, b: 260 },
  { text: "4512 7788 3301", l: 640, t: 270, r: 1000, b: 320 },
  { text: "Amount Payable / देय राशि", l: 60, t: 900, r: 520, b: 950 },
  { text: "₹ 1,240.00", l: 600, t: 905, r: 900, b: 960 },
  { text: "Due Date / अंतिम तिथि: 15/12/2026", l: 60, t: 1000, r: 800, b: 1050 },
];

describe("bill parser", () => {
  it("finds fields", () => {
    const f = parseBill(BILL);
    expect(f.account.value).toBe("451277883301");
    expect(f.amount.value).toBe("1,240");
    expect(f.due.value).toBe("15/12/2026");
  });
  it("never guesses", () => {
    const f = parseBill(BILL.map((l) => (l.text.startsWith("₹") ? { ...l, text: "₹ ~~,~~" } : l)));
    expect(f.amount.value).toBeNull();
    expect(f.amount.box).not.toBeNull();
  });
});

/** Drive the assistant like the UI would, with instant TTS. */
function harness() {
  const a = createAssistant();
  const said: string[] = [];
  a.subscribe((s) => { if (s.say) { const { id, text } = s.say; said.push(text); queueMicrotask(() => a.spoken(id)); } });
  const flush = () => new Promise((r) => setTimeout(r, 0));
  const step = async (f: (a: Assistant) => void) => { f(a); for (let i = 0; i < 20; i++) await flush(); };
  return { a, said, step, last: () => said[said.length - 1] };
}

describe("core workflow", () => {
  it("udhaar: similar names, read-back, save", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    expect(h.said).toContain("Batao, kya karna hai?");
    expect(h.a.state.listen).toBe(true);
    await h.step((a) => a.heard("Ramesh ko do sau udhaar"));
    expect(h.last()).toBe("Ramesh Kumar ya Ramesh Yadav?");
    await h.step((a) => a.heard("Kumar"));
    expect(h.last()).toBe("Ramesh Kumar, 200 rupaye udhaar. Sahi?");
    const before = h.a.ledger.balanceOf("Ramesh Kumar");
    await h.step((a) => a.yes());
    expect(h.a.ledger.balanceOf("Ramesh Kumar")).toBe(before + 200);
    expect(h.last()).toContain("Likh diya");
  });

  it("nothing happens before Haan; two Nahi -> pictures", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("Geeta ko sau udhaar"));
    const bal = h.a.ledger.balanceOf("Geeta Devi");
    await h.step((a) => a.no());
    expect(h.last()).toBe("Achha, phir se bataiye.");
    await h.step((a) => a.heard("Geeta ko sau udhaar"));
    await h.step((a) => a.no());
    expect(h.a.state.showPictures).toBe(true);
    expect(h.a.ledger.balanceOf("Geeta Devi")).toBe(bal);
  });

  it("over 2000 needs a second check", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("Salma ko teen hazaar udhaar"));
    await h.step((a) => a.yes());
    expect(h.last()).toBe("Do hazaar se zyada hai. Pakka?");
  });

  it("noise twice -> picture buttons", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("umm"));
    expect(h.last()).toContain("Thoda shor hai");
    await h.step((a) => a.noMatch());
    expect(h.a.state.showPictures).toBe(true);
  });

  it("reminder: draft, WhatsApp, Send (user taps)", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("Suresh se paisa kaise loon"));
    expect(h.last()).toContain("Suresh Pal ko paise ki yaad");
    await h.step((a) => a.yes());
    expect(h.last()).toBe("Suresh Pal ke 450 rupaye baaki hain. WhatsApp par yaad dilaun?");
    await h.step((a) => a.yes());
    expect(h.a.state.view.kind).toBe("reminder");
    await h.step((a) => a.share("whatsapp"));
    await h.step((a) => a.share("send"));
    expect(h.last()).toContain("Bhej diya");
  });

  it("bill: read aloud, then guide Demo Pay to success; PIN hides the guide", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("bijli ka bill bharna hai"));
    await h.step((a) => a.yes());
    expect(h.a.state.needBill).toBe(true);
    await h.step((a) => a.billOcr(BILL, 1080, 1440));
    expect(h.said).toContain("Upar daayein aapka account number hai: 4-5-1-2, 7-7-8-8, 3-3-0-1.");
    expect(h.last()).toBe("Bharne ka tarika bataun?");
    await h.step((a) => a.yes());
    const pay = () => h.a.state.view as any;
    expect(pay().highlight).toBe("bills_tile");
    await h.step((a) => a.payTap("send_tile"));            // wrong tap
    expect(pay().screen).toBe("other");
    expect(pay().highlight).toBe("back_btn");
    await h.step((a) => a.payTap("back_btn"));
    await h.step((a) => a.payTap("bills_tile"));
    expect(pay().highlight).toBe("electricity_tile");
    await h.step((a) => a.payTap("electricity_tile"));
    expect(pay().highlight).toBe("account_field");
    await h.step((a) => a.payType("account_field", "451277883301"));
    expect(pay().highlight).toBe("continue_btn");
    await h.step((a) => a.payTap("continue_btn"));
    await h.step((a) => a.payTap("pay_btn"));
    expect(pay().sensitive).toBe(true);
    expect(pay().highlight).toBeNull();
    await h.step((a) => a.payType("pin_field", "1234"));
    expect(JSON.stringify(h.a.state)).not.toContain("1234");   // PIN never stored
    await h.step((a) => a.payTap("pin_submit"));
    expect(pay().screen).toBe("success");
    expect(h.said.join(" ")).toContain("shabaash");
  });

  it("atak gaya mid-journey -> redacted summary -> send", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("bijli ka bill bharna hai"));
    await h.step((a) => a.yes());
    await h.step((a) => a.billOcr(BILL, 1080, 1440));
    await h.step((a) => a.yes());
    await h.step((a) => a.payTap("bills_tile"));
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("main atak gaya"));
    expect(h.last()).toBe("Aap atak gaye hain. Kisi apne se madad lein?");
    await h.step((a) => a.yes());
    const v = h.a.state.view as any;
    expect(v.kind).toBe("rescue");
    expect(v.summary).toContain("Screen: Bills");
    expect(v.summary).not.toContain("451277883301");
    expect(v.summary).not.toMatch(/4-5-1-2|7-7-8-8|3-3-0-1/);
    expect(v.summary).toContain("Kaam: Bijli ka bill bharna");
    expect(h.last()).toBe("Bhej doon?");
    await h.step((a) => a.yes());
    await h.step((a) => a.share("whatsapp"));
    await h.step((a) => a.share("send"));
    expect(h.a.cases.length).toBe(1);
  });

  it("unknown request -> Rescue Circle", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("mausam kaisa hai aaj"));
    expect(h.said).toContain("Ye main abhi nahi seekha. Kisi apne se madad maangein?");
    expect(h.a.state.view.kind).toBe("rescue");
  });

  it("new task clears the old view", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("kiska kitna baaki hai"));
    await h.step((a) => a.yes());
    expect(h.a.state.view.kind).toBe("ledger");
    await h.step((a) => a.micTapped());
    expect(h.a.state.view.kind).toBe("home");
  });

  it("new name is confirmed as new; Nahi asks for just the name", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("Rakesh ko sau udhaar"));
    expect(h.last()).toBe("Rakesh naya naam hai. Likh doon?");
    await h.step((a) => a.no());
    expect(h.last()).toBe("Sirf naam boliye.");
    await h.step((a) => a.heard("Ramesh Yadav"));
    expect(h.last()).toBe("Ramesh Yadav, 100 rupaye udhaar. Sahi?");
  });

  it("reminder for an unknown name does not invent a customer", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("Pappu se paisa kaise loon"));
    expect(h.last()).toBe("Pappu naam khaate mein nahi hai.");
  });

  it("atak gaya after a task names that task, not 'atak gaya' twice", async () => {
    const h = harness();
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("kiska kitna baaki hai"));
    await h.step((a) => a.yes());
    await h.step((a) => a.micTapped());
    await h.step((a) => a.heard("main atak gaya"));
    await h.step((a) => a.yes());
    const v = h.a.state.view as any;
    expect(v.summary).toContain("Kaam: Udhaar ka hisaab sunna");
    expect(v.summary.match(/atak gaya/g)?.length ?? 0).toBe(1);
  });
});
