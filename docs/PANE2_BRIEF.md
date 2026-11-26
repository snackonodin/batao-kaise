# Pane 2 brief (v3, 2026-11-27): Batao Kaise web demo UI + video

**Scope changed (user decision).** The submission is ONE core voice workflow, not a full product, delivered as a
**web demo** (no Android build needed). Deliverables: public GitHub code that runs with no keys, a YouTube demo (≤ 3 min),
a pitch deck PDF, and a live Vercel link.

The core workflow: tap mic → "Batao, kya karna hai?" → user speaks → app reads back → Haan/Nahi → the app does it, step by step.
When asked, it can do three things (they are skills of ONE workflow, not separate products):
1. **Udhaar**: "Ramesh ko do sau udhaar" / "Ramesh ne 100 de diye" / "Kiska kitna baaki hai?" / "Ramesh se paisa kaise loon?" (reminder text + UPI QR + mock WhatsApp share).
2. **Bill**: "Bijli ka bill bharna hai" → sample bill with coloured boxes read aloud → guided mock Demo Pay (highlight moves Bills → Electricity → account → Continue → Pay; PIN screen hides the guide).
3. **Atak gaya**: redacted summary → "Bhej doon?" → mock WhatsApp share to the trusted person.
Schemes are cut. The Android code in `app/` stays in the repo as a reference, frozen.

## Ownership
- **Pane 1 (other session):** `web-demo/src/engine/**` (all logic: normalizer, numbers, intents, dialogue state machine, ledger, bill parser, redactor, guide), its tests, `docs/deck.*`, `docs/demo-script.md`, `README.md`, `.github/`, `app/`, `demo-upi/`, `docs/CHECKPOINT.md`, this brief. Pane 1 also made the scaffold (`web-demo/package.json`, `vite.config.ts`, `tsconfig.json`).
- **You (pane 2):** everything else in `web-demo/` (`src/ui/**`, `src/App.tsx`, `src/main.tsx`, `src/styles.css`, `public/**`, `index.html`), `assets/demo/sample_bill.png` (+ copy to `web-demo/public/`), Vercel deploy, `docs/video/**`, `scripts/video/**`.
- Need a dependency? Add it yourself with `npm i` in web-demo (package.json is shared, pull first). Commit prefix `[p2]`.
- Never edit pane 1's files. Questions go in `docs/HANDOFF.md`. Push with `git pull --rebase origin main && git push`. Never force-push.
- Never run Gradle. Never `git add` from outside `~/projects/BK` (the home folder is also a git repo).
- Keys in `.env` (never open, print or commit it): `ELEVENLABS_API_KEY` is set. No Higgsfield key yet.

## The engine contract (read `web-demo/src/engine/index.ts`; pane 1 keeps it stable)
- `const a = createAssistant()`; `a.subscribe(state => render(state))`; `a.state`.
- The engine never touches the DOM, mic or speaker. It emits `state.say` (`{id, text}`) whenever it wants a line spoken:
  you speak it with `speechSynthesis` (hi-IN, rate 0.9; fall back to any voice) and call `a.spoken(id)` when the utterance ends.
- Input events you call: `a.micTapped()`, `a.partial(text)`, `a.heard(text)` (final transcript), `a.silence()`, `a.noMatch()`,
  `a.yes()`, `a.no()`, `a.done()`, `a.picture('bill'|'udhaar'|'madad')`, `a.payTap(elementId)` (user tapped something in mock Demo Pay),
  `a.payType(elementId, value)`, `a.billTap(field)`, `a.share(target)`, `a.reset()`.
- `state.listen === true` means: start Web Speech recognition (hi-IN, interimResults) now. "Type instead" box always calls `a.heard(text)`.
- `state.view` tells you what to show: `home | bill | pay | ledger | reminder | rescue`, with its data. For `pay`, `state.view.highlight`
  is the element id to outline (or null), and `state.view.screen` is which mock Demo Pay screen to show. Use exactly these element ids:
  `bills_tile`, `electricity_tile`, `account_field`, `continue_btn`, `pay_btn`, `pin_field`, `pin_submit`, `back_btn`. Demo Pay never moves
  screens by itself: when the user taps, call `a.payTap(id)` and render the screen the engine returns.
- Until pane 1 lands the engine, build against a stub with the same shape.

## Your order
1. `assets/demo/sample_bill.png` (1080x1440, crisp, fake): header "DEMO BILL, NOT REAL", "Demo Vidyut Vitran Nigam", "Sunita Devi",
   "Consumer No / उपभोक्ता संख्या" + `4512 7788 3301` (top right), "Amount Payable / देय राशि" + `₹ 1,240.00`, "Due Date / अंतिम तिथि: 15/12/2026".
   Render HTML in headless Chrome and screenshot. Write `web-demo/public/sample_bill.ocr.json`: an array of `{text, l, t, r, b}`
   lines with pixel boxes for every text line on the bill (the engine's parser runs on these: the web demo's OCR is precomputed, and the UI must say so).
2. UI: phone frame (looks like an Android phone, 360x780), warm off-white `#FBF7F0`, one deep green `#1F5E3B`, Mukta font (Google Fonts),
   buttons ≥ 56px, big mic with pulsing ring, big Hinglish bubble, live transcript, Haan/Nahi, picture buttons, "Ho gaya", "type instead" box.
   Banner above the phone: "Web demo; the real app is Android". A side panel (desktop) shows the English caption of what is happening, for judges.
3. Mock Demo Pay screens (Home, Bills, Electricity, Pay, PIN, Success), each labelled "DEMO, no real money", with the pulsing highlight + arrow.
   Bill view with coloured boxes over the image (Account no. blue, Rakam green, Aakhri tareekh orange, grey "Saaf nahi").
   Reminder view with text + QR (`qrcode` package) + a mock WhatsApp share sheet. Rescue view with the redacted summary, editable.
4. Deploy to Vercel (`npx vercel --prod` from web-demo; if login is needed, ask the user to run `! npx vercel login`). Put the URL in HANDOFF.md.
5. Video, per `docs/demo-script.md`: record the deployed demo (puppeteer screencast or ffmpeg), ElevenLabs voices, English captions, ffmpeg cut
   → `docs/video/batao-kaise-demo.mp4`. ElevenLabs: download its skills to a temp dir outside the repo
   (`curl -fsSL https://github.com/elevenlabs/skills/archive/refs/heads/main.tar.gz | tar -xz -C /tmp/el-skills`), read the text-to-speech
   SKILL.md + references first, install the SDK only in `scripts/video/`, load `.env` inside the script, never print the key. Afterwards run
   `npx -y @elevenlabs/cli@latest onboard check --written --env-path .env` and follow what it prints.
   The lower-third "Web demo simulating the Android app" stays on screen. AI footage only for an optional intro, captioned "Illustrative".
