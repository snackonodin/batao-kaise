// UI check: runs the whole demo flow in headless Chrome and saves a screenshot per step.
// Usage: node scripts/video/shots.mjs [baseUrl] [outDir]
import puppeteer from "puppeteer-core";
import { mkdirSync } from "node:fs";
import { helpers, scenes } from "./flow.mjs";

const base = process.argv[2] ?? "http://localhost:5179";
const out = process.argv[3] ?? new URL("./out/shots", import.meta.url).pathname;
mkdirSync(out, { recursive: true });

const browser = await puppeteer.launch({
  executablePath: process.env.CHROME ?? "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
  headless: true, defaultViewport: { width: 1280, height: 900, deviceScaleFactor: 1 },
});
const page = await browser.newPage();
const errors = [];
page.on("pageerror", (e) => errors.push(String(e)));
page.on("console", (m) => { if (m.type() === "error") errors.push(m.text()); });
await page.goto(`${base}/?tts=0&mic=0`, { waitUntil: "networkidle0" });
await page.evaluate(() => document.fonts.ready);

const phone = async (name) => {
  const el = await page.$(".phone");
  await el.screenshot({ path: `${out}/${name}.png` });
  console.log("shot", name);
};
const h = helpers(page, { shot: phone, pace: 0.3 });
const sc = scenes(h, page);
try {
  await page.screenshot({ path: `${out}/00-page.png` });
  for (const name of ["home", "bill", "udhaar", "remind", "noise", "wrongName", "rescue"]) { await sc[name](); console.log("scene ok", name); }
  await page.setViewport({ width: 390, height: 844, deviceScaleFactor: 1 });
  await page.screenshot({ path: `${out}/99-mobile.png`, fullPage: true });
} catch (e) {
  console.error("FAILED:", e.message);
  await page.screenshot({ path: `${out}/zz-failure.png` });
  process.exitCode = 1;
}
if (errors.length) { console.error("page errors:", errors); process.exitCode = 1; }
await browser.close();
