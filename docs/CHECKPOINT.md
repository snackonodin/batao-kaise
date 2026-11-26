# Checkpoint (2026-11-27, ~03:15)

## Scope (user decision, final)
ONE core voice workflow, delivered as a **web demo** (no Android build needed). Submission = public GitHub code that runs with no keys,
a YouTube video (≤ 3 min), `docs/deck.pdf`, and a live Vercel link. The workflow's three skills: udhaar, bill (read + guided Demo Pay), Rescue Circle.
Schemes are cut. The Android code (`app/`, `demo-upi/`) is a frozen reference implementation, untested on a device.

## Panes (tmux: %0 = pane 1, %1 = pane 2; both run in ~/projects/BK and share one working folder)
- Pane 1: engine + tests, README, CI, deck, demo script, this file. Pane 2: UI, assets, video, Vercel deploy.
- Pull with `git pull --rebase --autostash origin main && git push`. Never force-push.

## Submission status
| Deliverable | Status |
|---|---|
| Public GitHub code, runs with no keys | **Done**: https://github.com/snackonodin/batao-kaise. CI green (19 tests + build). |
| Demo video ≤ 3 min | **Done**: `docs/video/batao-kaise-demo.mp4` (2:59, 1080p, ElevenLabs voices, recorded from the real build). **You upload it to YouTube** (public or unlisted), then paste the link into the README line 5. |
| Pitch deck PDF | **Done**: `docs/deck.pdf` (10 slides: problem, users, workflow, guide, Rescue Circle, dependencies, safety, launch plan, ask). |
| Live link (optional) | **Blocked on your Vercel login.** In pane 2: `! cd web-demo && npx vercel login`, then press Enter on the "deploy" already typed there. Pane 2 runs `npx vercel --prod` and writes the URL into HANDOFF.md; then add it to README line 5. (A no-login `--temporary` deploy was tried: it expires after 60 min, so it's no use.) |

## Final report: every feature
| Feature | Status |
|---|---|
| Mic → "Batao, kya karna hai?" → speak → read-back → Haan → act | **Working** (browser Web Speech hi-IN + speechSynthesis; checked by hand in Chrome on the production build) |
| Typing / clicking a phrase instead of speaking | **Working** (fallback) |
| Hindi/Hinglish parsing: Devanagari, synonyms, spoken numbers | **Working**, rule-based; 20 test phrases pass |
| Udhaar add/repay, similar names asked about, new names confirmed, >₹2000 second check | **Working**, stored in the browser (localStorage) |
| "Kiska kitna baaki" balances read aloud | **Working** |
| Reminder: WhatsApp draft + UPI QR, user presses Send | **Working**; the WhatsApp screen is a **mock** |
| Bill read-aloud with coloured boxes, account read in groups, "Saaf nahi" when unclear | Parser **working**; OCR text of the sample bill is **precomputed** (mocked OCR) |
| Guided Demo Pay: highlights each button, wrong-screen recovery, steps back at PIN, PIN never stored | **Working** on a **mock** bank app |
| Rescue Circle: summary from task state, redaction, edit, "Bhej doon?", share | **Working**; the share target is a **mock** |
| Noise / silence / two Nahi → picture buttons; ruko, phir se, peeche, naya kaam | **Working** |
| Wispr Flow speech, LLM intent | **Not built** (stubs) |
| Android app (`app/`, `demo-upi/`) | Builds, unit tests passed earlier; **untested on a device**; not part of the demo |
