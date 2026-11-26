// Records the demo video footage from the real web demo, following docs/demo-script.md.
//   node record.mjs collect   -> runs the flow once and saves every line the app speaks (out/app_lines.json)
//   node record.mjs           -> records out/raw.webm with each app line held exactly as long as its ElevenLabs audio,
//                                and writes out/timeline.json (when each voice clip starts) for build.mjs
// The page is the production build (vite preview), opened with ?tts=0&mic=0&rec=1.
import puppeteer from "puppeteer-core";
import { mkdirSync, readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
import { synth } from "./tts.mjs";

const COLLECT = process.argv[2] === "collect";
const BASE = process.env.DEMO_URL ?? "http://localhost:5180";
const OUT = new URL("./out/", import.meta.url).pathname;
mkdirSync(OUT, { recursive: true });
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const GAP = 100; // breath after each app line

// ---------- voice clips ----------
const appLines = !COLLECT && existsSync(`${OUT}app_lines.json`) ? JSON.parse(readFileSync(`${OUT}app_lines.json`, "utf8")) : [];
const sayMs = {};
if (!COLLECT) for (const t of appLines) sayMs[t] = (await synth("app", t)).ms + GAP;

// ---------- browser ----------
const browser = await puppeteer.launch({
  executablePath: process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  headless: true, args: ["--hide-scrollbars"],
  defaultViewport: { width: 1600, height: 900, deviceScaleFactor: 1.2 },
});
const page = await browser.newPage();
const timeline = [];  // { t, role, text }
const spoken = new Set();
let t0 = Date.now();
await page.exposeFunction("__log", (role, text) => { timeline.push({ t: Date.now() - t0, role, text }); if (role === "app") spoken.add(text); });
await page.evaluateOnNewDocument((ms, gap) => {
  window.__onSay = (t) => window.__log("app", t);
  window.__sayMs = (t) => ms[t] ?? Math.min(9000, 700 + t.length * 58) + gap;
}, sayMs, GAP);
page.on("pageerror", (e) => console.error("pageerror", e.message));
await page.goto(`${BASE}/?tts=0&mic=0&rec=1`, { waitUntil: "networkidle0" });
await page.evaluate(() => document.fonts.ready);

// ---------- recording stage: captions, lower-third, title cards, touch cursor ----------
await page.addStyleTag({ content: `
  .banner, .side { display: none !important; }
  .page { padding: 0; height: 100vh; justify-content: center; }
  .stage { justify-content: flex-start; padding-left: 210px; max-width: none; }
  #rec-cap { position: fixed; left: 700px; right: 120px; top: 300px; font: 800 46px/1.18 Mukta, sans-serif; color: #1B1B18; text-wrap: balance; transition: opacity .35s; }
  #rec-cap small { display: block; margin-top: 18px; font: 600 26px/1.3 Mukta, sans-serif; color: #5B5A55; }
  #rec-lower { position: fixed; left: 700px; bottom: 56px; padding: 10px 18px; border: 2px solid #E4DED3; border-radius: 14px; background: #FBF7F0; font: 600 21px/1.3 Mukta, sans-serif; color: #5B5A55; }
  #rec-lower b { color: #1F5E3B; }
  #rec-card { position: fixed; inset: 0; z-index: 100; background: #1F5E3B; color: #fff; display: flex; flex-direction: column; justify-content: center; padding: 0 160px; transition: opacity .6s; }
  #rec-card h1 { font: 800 120px/1 Mukta, sans-serif; margin: 0 0 24px; }
  #rec-card p { font: 600 40px/1.3 Mukta, sans-serif; margin: 0; color: #E3EFE7; max-width: 1100px; }
  #rec-card small { display: block; margin-top: 48px; font: 600 26px/1.3 Mukta, sans-serif; color: #E3EFE7; opacity: .85; }
  #rec-cursor { position: fixed; z-index: 40; width: 46px; height: 46px; margin: -23px 0 0 -23px; border-radius: 50%; background: rgba(27,27,24,.22); border: 3px solid rgba(255,255,255,.9); box-shadow: 0 2px 6px rgba(0,0,0,.3); left: 380px; top: 860px; opacity: 0; transition: left .6s cubic-bezier(.3,.7,.2,1), top .6s cubic-bezier(.3,.7,.2,1), opacity .3s, transform .15s; pointer-events: none; }
  #rec-cursor.down { transform: scale(.78); background: rgba(27,27,24,.38); }
`});
await page.evaluate(() => {
  const add = (id, html = "") => { const d = document.createElement("div"); d.id = id; d.innerHTML = html; document.body.appendChild(d); return d; };
  add("rec-cap");
  add("rec-lower", "<b>Web demo</b> simulating the Android app. The real app is in the GitHub repo.");
  add("rec-cursor");
  const card = add("rec-card"); card.style.opacity = "0"; card.style.pointerEvents = "none";
});

const caption = (text, sub = "") => page.evaluate((t, s) => {
  const el = document.getElementById("rec-cap");
  el.style.opacity = "0";
  setTimeout(() => { el.innerHTML = t ? `${t}${s ? `<small>${s}</small>` : ""}` : ""; el.style.opacity = "1"; }, 350);
}, text, sub);
const card = (html) => page.evaluate((h) => {
  const c = document.getElementById("rec-card");
  if (h) c.innerHTML = h;
  c.style.opacity = h ? "1" : "0";
  document.getElementById("rec-lower").style.visibility = h ? "hidden" : "visible";
  document.getElementById("rec-cursor").style.visibility = h ? "hidden" : "visible";
}, html);

// ---------- helpers ----------
const S = () => page.evaluate(() => { const s = window.__batao.state; return { ...s, view: { ...s.view } }; });
async function until(pred, label, timeout = 40000) {
  const start = Date.now();
  for (;;) {
    const s = await S();
    if (pred(s)) return s;
    if (Date.now() - start > timeout) throw new Error(`timeout: ${label} (bubble="${s.bubble}", view=${s.view.kind})`);
    await sleep(60);
  }
}
const quiet = (label) => until((s) => !s.say, label);
const listening = (label) => until((s) => s.listen, label);

async function tap(selector, { hold = 380 } = {}) {
  await page.waitForSelector(selector, { visible: true });
  const box = await (await page.$(selector)).boundingBox();
  const x = box.x + box.width / 2, y = box.y + box.height / 2;
  await page.evaluate((x, y) => { const c = document.getElementById("rec-cursor"); c.style.opacity = "1"; c.style.left = x + "px"; c.style.top = y + "px"; }, x, y);
  await sleep(hold);
  await page.evaluate(() => document.getElementById("rec-cursor").classList.add("down"));
  await sleep(140);
  await page.click(selector);
  await page.evaluate(() => document.getElementById("rec-cursor").classList.remove("down"));
  await sleep(250);
}
const hideCursor = () => page.evaluate(() => { document.getElementById("rec-cursor").style.opacity = "0"; });
const tapTarget = (id) => tap(`[data-target="${id}"] > button, [data-target="${id}"] > input`);

/** The shopkeeper speaks: live transcript fills word by word over the clip's length, then the final transcript. */
async function user(text, { heard = text } = {}) {
  await listening(`listening for "${text}"`);
  await sleep(250);
  const { ms } = await synth("user", text);
  await page.evaluate((t) => window.__log("user", t), text);
  const words = text.replace(/[.?!]$/, "").split(" ");
  for (let i = 1; i <= words.length; i++) {
    await page.evaluate((p) => window.__batao.partial(p), words.slice(0, i).join(" "));
    await sleep(ms / words.length);
  }
  await sleep(200);
  await page.evaluate((h) => window.__batao.heard(h), heard);
}
async function narrator(text) {
  const { ms } = await synth("narrator", text);
  await page.evaluate((t) => window.__log("narrator", t), text);
  await sleep(ms);
}
const yes = async () => { await until((s) => s.showYesNo && s.listen, "yes/no"); await user("Haan."); };
const no = async () => { await until((s) => s.showYesNo && s.listen, "yes/no"); await user("Nahi."); };

// ---------- the film ----------
// Frames come from CDP with their real timestamps (Puppeteer's screencast drops idle time, which breaks audio sync).
let rec = null;
const frames = [];
if (!COLLECT) {
  rmSync(`${OUT}frames`, { recursive: true, force: true });
  mkdirSync(`${OUT}frames`, { recursive: true });
  const cdp = await page.createCDPSession();
  cdp.on("Page.screencastFrame", async ({ data, metadata, sessionId }) => {
    const n = frames.length;
    frames.push({ file: `frames/${String(n).padStart(6, "0")}.jpg`, ts: Date.now(), chrome: metadata.timestamp * 1000 });
    writeFileSync(`${OUT}${frames[n].file}`, Buffer.from(data, "base64"));
    try { await cdp.send("Page.screencastFrameAck", { sessionId }); } catch { /* closing */ }
  });
  await cdp.send("Page.startScreencast", { format: "jpeg", quality: 92, maxWidth: 1920, maxHeight: 1080, everyNthFrame: 1 });
  rec = { stop: () => cdp.send("Page.stopScreencast") };
  await sleep(500);
}
t0 = Date.now();
timeline.length = 0;

try {
  // 0:00 intro card
  await card(`<h1>Batao Kaise</h1><p>Speak, and your phone shows you how.</p>`);
  await sleep(600);
  await caption("");
  await narrator("Gaon mein sabke paas phone hai. Par use karna har koi nahi jaanta.");
  await caption("Everyone has a phone. Not everyone can use it fully.");
  await sleep(300);
  await card(null);
  await sleep(600);

  // 0:10 one button
  await caption("One button. Speak in Hindi or Hinglish.");
  await sleep(450);
  await tap(".mic"); await hideCursor();
  await user("Bijli ka bill bharna hai.");
  await caption("It always reads back what it heard.", "Nothing happens before “Haan”.");
  await yes();

  // bill read aloud
  await until((s) => s.view.kind === "bill" && !s.view.loading, "bill");
  await caption("It reads the bill aloud: numbers digit by digit.", "Unclear fields are marked “Saaf nahi”, never guessed. (Web demo: OCR precomputed; Android runs ML Kit on the phone.)");
  await yes(); // Bharne ka tarika bataun?

  // Demo Pay, guided
  await until((s) => s.view.kind === "pay", "pay");
  await caption("It highlights the exact button.", "It never taps for you. Demo Pay is a dummy app: no real money.");
  await quiet("bills line"); await tapTarget("bills_tile");
  await until((s) => s.view.highlight === "electricity_tile", "electricity"); await quiet("electricity line"); await tapTarget("electricity_tile");
  await until((s) => s.view.highlight === "account_field", "account"); await quiet("account line");
  await tapTarget("account_field");
  await page.type("#account_field", "451277883301", { delay: 170 });
  await until((s) => s.view.highlight === "continue_btn", "continue"); await quiet("continue line"); await tapTarget("continue_btn");
  await until((s) => s.view.highlight === "pay_btn", "pay"); await quiet("pay line"); await tapTarget("pay_btn");
  await until((s) => s.view.screen === "pin", "pin");
  await caption("On PIN and OTP screens the guide steps back.", "The user types secrets alone.");
  await quiet("pin line");
  await tap("#pin_field"); await page.type("#pin_field", "1234", { delay: 260 });
  await tap(".pin .pay-primary"); await hideCursor();
  await until((s) => s.view.screen === "success", "success");
  await caption("Done by the user, on their own phone.");
  await quiet("shabaash"); await sleep(500);

  // 0:55 udhaar: a misheard name, then the similar-name check
  await caption("A voice udhaar notebook.", "Misheard? Say “Nahi”. Similar names are checked before anything is written.");
  await tap(".mic"); await hideCursor();
  await user("Rakesh ko do sau udhaar.");
  await no();
  await until((s) => s.listen && /naam/.test(s.bubble), "sirf naam");
  await user("Ramesh.", { heard: "Ramesh" });
  await until((s) => s.listen && / ya /.test(s.bubble), "which Ramesh");
  await user("Kumar.", { heard: "Kumar" });
  await yes();
  await until((s) => s.view.kind === "ledger", "ledger"); await quiet("likh diya"); await sleep(400);

  // 1:10 reminder with UPI QR
  await caption("A polite reminder with a UPI QR.", "The user sends it themselves, on WhatsApp.");
  await tap(".mic"); await hideCursor();
  await user("Ramesh Kumar se paisa kaise loon?");
  await yes();
  await until((s) => s.showYesNo && /WhatsApp/.test(s.bubble), "yaad dilaun");
  await yes();
  await until((s) => s.view.kind === "reminder", "reminder"); await quiet("whatsapp line");
  await tapTarget("share_whatsapp");
  await until((s) => s.view.highlight === "send", "send"); await quiet("send line");
  await tapTarget("send"); await hideCursor();
  await until((s) => s.view.stage === "sent", "sent"); await quiet("bhej diya"); await sleep(400);

  // 1:45 noise -> pictures -> Phone madad (Rescue Circle)
  await caption("Too noisy? It asks again,", "then shows picture buttons.");
  await tap(".mic"); await hideCursor();
  await user("umm", { heard: "umm" });
  await listening("noisy retry");
  await user("umm", { heard: "umm" });
  await until((s) => s.showPictures, "pictures"); await quiet("tasveer"); await sleep(400);
  await caption("Stuck? Ask someone you trust.", "Rescue Circle sends a clear summary. OTPs, PINs, account, Aadhaar, phone and UPI numbers are removed first.");
  await tap(".pic:nth-child(3)"); await hideCursor();
  await yes();
  await until((s) => s.view.kind === "rescue" && s.showYesNo, "bhej doon");
  await yes();
  await until((s) => s.view.highlight === "share_whatsapp", "share"); await quiet("whatsapp");
  await tapTarget("share_whatsapp");
  await until((s) => s.view.highlight === "send", "send"); await quiet("send line");
  await tapTarget("send"); await hideCursor();
  await until((s) => s.view.stage === "sent", "sent"); await quiet("jaldi madad"); await sleep(500);

  // outro
  await card(`<h1>Batao Kaise</h1><p>Confidence: people learn to do it on their own phone, by themselves.</p><small>github.com/snackonodin/batao-kaise · Android app + this web demo · fake data only</small>`);
  await sleep(700);
  await narrator("Batao Kaise. Apne phone par, khud se.");
  await sleep(500);
} catch (e) {
  console.error("FAILED:", e.message);
  await page.screenshot({ path: `${OUT}record-failure.png` });
  process.exitCode = 1;
}

const total = Date.now() - t0;
if (rec) await rec.stop();
await browser.close();

if (COLLECT) {
  writeFileSync(`${OUT}app_lines.json`, JSON.stringify([...spoken], null, 1));
  console.log(`collected ${spoken.size} app lines`);
} else {
  const missing = [...spoken].filter((t) => !(t in sayMs));
  writeFileSync(`${OUT}timeline.json`, JSON.stringify({ total, events: timeline }, null, 1));
  // ffconcat list: each frame lasts until the next one; times relative to t0 (frames before t0 are skipped).
  const rel = frames.map((f) => ({ ...f, t: f.ts - t0 })).filter((f, i, a) => i === a.length - 1 || a[i + 1].t > 0);
  let list = "ffconcat version 1.0\n";
  for (let i = 0; i < rel.length; i++) {
    const start = Math.max(0, rel[i].t), end = i + 1 < rel.length ? rel[i + 1].t : total;
    if (end - start <= 0) continue;
    list += `file '${rel[i].file}'\nduration ${((end - start) / 1000).toFixed(4)}\n`;
  }
  list += `file '${rel[rel.length - 1].file}'\n`;
  writeFileSync(`${OUT}frames.ffconcat`, list);
  const drift = frames.filter((_, i) => i % Math.max(1, Math.floor(frames.length / 8)) === 0).map((f) => `${((f.ts - t0) / 1000).toFixed(1)}s:${(f.ts - f.chrome).toFixed(0)}ms`);
  console.log(`${frames.length} frames captured; receipt minus chrome timestamp: ${drift.join(" ")}`);
  console.log(`recorded ${(total / 1000).toFixed(1)}s, ${timeline.length} voice events${missing.length ? `; ${missing.length} app lines had no audio (run collect again): ${missing.join(" | ")}` : ""}`);
}
