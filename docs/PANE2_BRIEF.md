# Pane 2 brief: Batao Kaise (non-Android work)

You are the second Claude session on this repo (`~/projects/BK`). Pane 1 owns the Android code.
The full product spec is in `docs/SPEC.md`. Read it first.

## Hard ownership rules (avoid edit collisions)
- **You own:** `web-demo/`, `docs/` (except `docs/PANE2_BRIEF.md` and `docs/SPEC.md`), `assets/demo/`, `backend/`, `README.md`, `LICENSE`, `.github/workflows/`.
- **Never edit:** `app/`, `demo-upi/`, `settings.gradle.kts`, `build.gradle.kts`, `gradle/`, root Gradle files.
- **Never run Gradle.** The Mac has 8 GB RAM; pane 1 runs the builds.
- Commit only your own paths: `git add <your paths>`, never `git add -A`. Small commits, message prefix `[p2]`.
- Never commit secrets. Keys live only in `.env` (gitignored).
- To ask pane 1 something, append to `docs/HANDOFF.md` under "Questions for pane 1". Pane 1 answers there.

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

## Then, in order
6. `docs/demo-script.md` (spec section 16, under 3:00, with English captions).
7. `docs/deck.md` in Marp (spec section 17, 10 slides), exported with `npx @marp-team/marp-cli docs/deck.md --pdf --allow-local-files -o docs/deck.pdf`.
   Use ONLY the numbers in the spec; mark the pilot figures as targets.
8. `web-demo/`: Vite + React, spec section 13. Deploy to **Vercel** (the user chose Vercel over GitHub Pages).
   Put `demo_form.html` and `sample_bill.png` in `web-demo/public/`. Banner: "Web demo; the real app is Android".
9. `backend/` (FastAPI): `/wispr/token` (mints a short-lived client token if WISPR_FLOW_API_KEY is set, else 503)
   and `/intent` (LLM returns ONLY `{intent, details}` JSON). Include requirements.txt and a README section.
10. `.github/workflows/build.yml`: JDK 17, build `:app:assembleDebug` and `:demo-upi:assembleDebug`,
    upload artifacts, and on tag `v*` attach both APKs to a GitHub Release.
11. `README.md` (spec section 4) and `LICENSE` (MIT). Leave a placeholder for the screenshot; pane 1 supplies it.

## Video (after pane 1 has stage 3 working)
Raw phone footage is recorded by pane 1 into `docs/video/raw/`. You do the edit with ffmpeg: captions, voiceover,
and an optional Higgsfield intro clip captioned "illustrative". Never present AI footage as the real app.
