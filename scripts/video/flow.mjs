// Drives the web demo through the whole core workflow with real clicks and typing.
// Used for UI screenshots (shots.mjs) and the screen recording (record.mjs).
// The demo is opened with ?tts=0&mic=0: speech is replaced by timers, input goes through the "type instead" path.

export const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

export function helpers(page, { shot = async () => {}, pace = 1 } = {}) {
  const S = () => page.evaluate(() => {
    const s = window.__batao.state;
    return { ...s, view: { ...s.view } };
  });
  const until = async (pred, label, timeout = 30000) => {
    const t0 = Date.now();
    for (;;) {
      const s = await S();
      if (pred(s)) return s;
      if (Date.now() - t0 > timeout) throw new Error(`timeout waiting for ${label}: phase=${s.phase} bubble="${s.bubble}" view=${s.view.kind}`);
      await sleep(80);
    }
  };
  const idle = (label) => until((s) => !s.say, label);
  const listening = (label) => until((s) => s.listen, label);
  const click = async (sel) => { await page.waitForSelector(sel, { visible: true }); await page.click(sel); };
  /** Type into the "type instead" box like a user would, then send. */
  const say = async (text) => {
    if (!(await page.$(".type-box input"))) await click(".type-toggle");
    await page.click(".type-box input");
    await page.type(".type-box input", text, { delay: 45 * pace });
    await sleep(250 * pace);
    await click(".type-box button[type=submit]");
  };
  const pause = (ms) => sleep(ms * pace);
  const tapTarget = (id) => click(`[data-target="${id}"] > button, [data-target="${id}"] > input`);
  return { S, until, idle, listening, click, say, pause, tapTarget, shot };
}

/** The scenes of the demo, in order. Each scene returns when the app is waiting for the user again. */
export function scenes(h, page) {
  const { until, idle, listening, click, say, pause, tapTarget, shot } = h;
  const yes = async () => { await until((s) => s.showYesNo && s.listen, "yes/no"); await pause(600); await click(".btn-yes"); };
  const no = async () => { await until((s) => s.showYesNo && s.listen, "yes/no"); await pause(600); await click(".btn-no"); };

  return {
    async home() { await pause(1200); await shot("01-home"); },

    async bill() {
      await click(".mic"); await listening("first question"); await pause(500);
      await say("bijli ka bill bharna hai");
      await until((s) => s.showYesNo, "bill read-back"); await shot("02-readback");
      await yes();
      await until((s) => s.view.kind === "bill" && !s.view.loading, "bill fields"); await pause(800); await shot("03-bill");
      await yes(); // "Bharne ka tarika bataun?"
      await until((s) => s.view.kind === "pay" && s.view.highlight === "bills_tile", "pay home"); await idle("bills line"); await shot("04-pay-home");
      await pause(500); await tapTarget("bills_tile");
      await until((s) => s.view.highlight === "electricity_tile", "electricity"); await idle("electricity line"); await shot("05-pay-bills");
      await pause(500); await tapTarget("electricity_tile");
      await until((s) => s.view.highlight === "account_field", "account"); await idle("account line"); await shot("06-pay-account");
      await page.click("#account_field"); await page.type("#account_field", "451277883301", { delay: 110 * (h.pace ?? 1) });
      await until((s) => s.view.highlight === "continue_btn", "continue"); await idle("continue line"); await shot("07-pay-continue");
      await pause(400); await tapTarget("continue_btn");
      await until((s) => s.view.highlight === "pay_btn", "pay"); await idle("pay line"); await shot("08-pay-pay");
      await pause(400); await tapTarget("pay_btn");
      await until((s) => s.view.screen === "pin", "pin"); await pause(600); await shot("09-pay-pin");
      await page.click("#pin_field"); await page.type("#pin_field", "1234", { delay: 160 });
      await pause(300); await click(".pin .pay-primary");
      await until((s) => s.view.screen === "success", "success"); await pause(900); await shot("10-pay-success");
      await idle("shabaash");
    },

    async udhaar() {
      await click(".mic"); await listening("task"); await pause(400);
      await say("Ramesh ko do sau udhaar");
      await until((s) => s.listen && /ya/.test(s.bubble) && !s.showYesNo, "which Ramesh"); await shot("11-which-ramesh");
      await say("Kumar");
      await until((s) => s.showYesNo, "udhaar read-back"); await shot("12-udhaar-readback");
      await yes();
      await until((s) => s.view.kind === "ledger", "ledger"); await idle("likh diya"); await pause(600); await shot("13-ledger");
    },

    async remind() {
      await click(".mic"); await listening("task"); await pause(400);
      await say("Ramesh se paisa kaise loon?");
      await until((s) => s.listen && /ya/.test(s.bubble) && !s.showYesNo, "which Ramesh");
      await say("Kumar");
      await until((s) => s.showYesNo, "remind read-back"); await yes();
      await until((s) => s.showYesNo && /WhatsApp/.test(s.bubble), "yaad dilaun"); await yes();
      await until((s) => s.view.kind === "reminder", "reminder"); await idle("WhatsApp dabaiye"); await pause(500); await shot("14-reminder");
      await tapTarget("share_whatsapp");
      await until((s) => s.view.highlight === "send", "chat"); await idle("send line"); await shot("15-reminder-chat");
      await pause(400); await tapTarget("send");
      await until((s) => s.view.stage === "sent", "sent"); await idle("bhej diya"); await shot("16-reminder-sent");
    },

    async noise() {
      await click(".mic"); await listening("task"); await pause(400);
      await say("umm");
      await listening("noisy retry"); await shot("17-noisy");
      await say("umm");
      await until((s) => s.showPictures, "pictures"); await idle("tasveer"); await shot("18-pictures");
    },

    async wrongName() {
      await click(".pic:nth-child(2)"); // Udhaar picture: reads the balances, ends the noisy branch
      await yes(); // "Aap sabka baaki hisaab sunna chahte hain. Sahi?"
      await until((s) => s.view.kind === "ledger", "balances");
      await until((s) => !s.say && !s.listen, "balances done", 60000);
      await click(".mic"); await listening("task"); await pause(400);
      await say("Rakesh ko sau udhaar");
      await until((s) => s.showYesNo, "rakesh read-back"); await shot("19-rakesh");
      await no();
      await listening("phir se"); await say("Ramesh Yadav ko sau udhaar");
      await until((s) => s.showYesNo, "yadav read-back"); await yes();
      await until((s) => s.view.kind === "ledger" && s.view.highlight === "Ramesh Yadav", "ledger yadav"); await idle("likh diya");
    },

    async rescue() {
      await click(".mic"); await listening("task"); await pause(400);
      await say("main atak gaya");
      await until((s) => s.showYesNo, "stuck read-back"); await yes();
      await until((s) => s.view.kind === "rescue" && s.showYesNo, "bhej doon"); await shot("20-rescue");
      await yes();
      await until((s) => s.view.highlight === "share_whatsapp", "share"); await idle("whatsapp line"); await shot("21-rescue-share");
      await tapTarget("share_whatsapp");
      await until((s) => s.view.highlight === "send", "chat"); await idle("send line");
      await pause(400); await tapTarget("send");
      await until((s) => s.view.stage === "sent", "sent"); await idle("bhej diya"); await shot("22-rescue-sent");
    },
  };
}
