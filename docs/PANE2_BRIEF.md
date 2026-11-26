# Pane 2 brief: Batao Kaise (non-Android work)

You are the second Claude session on this repo (`~/projects/BK`). Pane 1 owns the Android code.
The full product spec is in `docs/SPEC.md`. Read it first.

## Hard ownership rules (avoid edit collisions)
- **You own:** `web-demo/`, `assets/demo/` (except seeded_ledger.json), `docs/video/`, `scripts/video/`.
- **Pane 1 owns (do NOT do these, re-split 2026-11-27):** `app/`, `demo-upi/`, `backend/`, `README.md`, `LICENSE`, `.github/workflows/`, `docs/deck.md`, `docs/deck.pdf`, `docs/demo-script.md`, `docs/CHECKPOINT.md`, `docs/SPEC.md`, this brief.
- **Never edit:** `app/`, `demo-upi/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, root Gradle files.
- **Never run Gradle.** The Mac has 8 GB RAM; pane 1 runs the builds.
- Commit only your own paths: `git add <your paths>`, never `git add -A`. Small commits, message prefix `[p2]`.
- Never commit secrets. Keys live only in `.env` (gitignored).
- To ask pane 1 something, append to `docs/HANDOFF.md` under "Questions for pane 1". Pane 1 answers there.
- `docs/CHECKPOINT.md` is pane 1's file; don't edit it.
- GitHub: repo is public at https://github.com/snackonodin/batao-kaise (gh is logged in). Push with
  `git pull --rebase origin main && git push`. Never force-push. `seeded_ledger.json` in assets/demo is pane 1's.
- Keys in `.env` (never open, print or commit it): `ELEVENLABS_API_KEY` is set. `HIGGSFIELD_API_KEY`/`_SECRET` are
  not set yet. For video tooling, load `.env` inside your script's process; keys never go into the app or the web demo.
- Never `git add` from outside `~/projects/BK`: the home folder `~` is also a git repo.

## Shared contracts (pane 1 reads these, so write them first, in this order)
1. `assets/demo/sample_bill.png`: a fake electricity bill (1080x1440, Hindi + English labels).
   Fields: Consumer/Account No `4512 7788 3301`, Amount `₹1,240`, Due date `15/12/2026`, name "Sunita Devi",
   board "Demo Vidyut Vitran Nigam", header "DEMO BILL, NOT REAL". Make the text crisp so ML Kit can read it
   (render HTML with headless Chrome, then screenshot). Also write `assets/demo/sample_bill.fields.json`
   with the expected values and pixel boxes.
2. `assets/demo/schemes.json`: 6 demo schemes (e.g. PM-Kisan-like, pension, ration, Ujjwala-like, scholarship,
   health is EXCLUDED, so no health schemes). Each: id, name_hi, name_en, rules (age/land/income/family/state),
   documents[], label "demo, verify on official site".
3. `assets/demo/seeded_ledger.json`: DONE by pane 1 (format is parsed by the app). Do not edit.
4. `assets/demo/demo_form.html`: follow the exact contract in `docs/HANDOFF.md` (ids, headings, 3 pages).
   Copy it to `web-demo/public/` too. Report the deployed Vercel URL in HANDOFF.md.
5. (Test phrases are owned by pane 1, inside `app/`. Skip.)
   Note: pane 1's app module bundles everything in `assets/demo/` as Android assets, so keep file names stable.
   Demo Pay's account number is `451277883301` (printed as `4512 7788 3301`); the bill must match it.

## PRIORITY CHANGE (user decision): no Android device. Testing and the demo video use the web demo on Vercel.
So after the shared contracts, your order is:
6. `web-demo/` (spec section 13): Vite + React, phone-frame simulation. It is now the main demo, so make it solid:
   core loop with read-back + Haan/Nahi, Web Speech API hi-IN mic + "type instead" box, bill reader with boxes on sample_bill.png,
   udhaar add/balance/reminder draft with UPI QR, mock Demo Pay with the moving guide highlight (Bills -> Electricity -> account -> Pay),
   Rescue Circle with redaction. Port the rules from the Kotlin sources (read-only for you): `app/src/main/java/com/bataokaise/app/voice/*.kt`,
   `intent/Intent.kt`, `rescue/Redactor.kt`, `debug/TestPhrases.kt` (all 20 phrases must pass in a JS test too), journeys in `app/src/main/assets/journeys/`.
   Banner: "Web demo; the real app is Android". Deploy to Vercel (`npx vercel login` once; the user runs it if needed). Write the URL in HANDOFF.md.
7. Video (see below), after the web demo works.

## Video (after the web demo works)
- Follow `docs/demo-script.md` (pane 1 writes it; under 3:00, English captions).
- Footage: record the deployed web demo in Chrome (e.g. headless Chrome + puppeteer screencast, or ffmpeg avfoundation screen capture).
  Label it as the web demo on screen. Never present AI footage or the web demo as the real Android app.
- Voiceover with **ElevenLabs** (key in `.env` as ELEVENLABS_API_KEY; the user chose this):
  1. Run `curl -fsSL -o /tmp/el-skills.tgz https://github.com/elevenlabs/skills/archive/refs/heads/main.tar.gz` into a temp dir outside the repo, extract, and read the text-to-speech skill's SKILL.md + references before writing code.
  2. Install the SDK only in `scripts/video/` (its own package.json or requirements.txt), load `.env` in the script process, never print the key.
  3. Generate Hindi/Hinglish narration + the user's spoken lines into `docs/video/audio/` (gitignored raw is fine; commit only small final files).
  4. Then run `npx -y @elevenlabs/cli@latest onboard check --written --env-path .env` and follow what it prints.
- Optional Higgsfield intro clip only if `HIGGSFIELD_API_KEY` appears in `.env`; caption it "illustrative".
- Final cut with ffmpeg -> `docs/video/batao-kaise-demo.mp4`.
