# Checkpoint (2026-11-27, ~01:40)

## Scope (user decision, final)
ONE core voice workflow, delivered as a **web demo** (no Android build needed). Submission = public GitHub code that runs with no keys,
a YouTube video (≤ 3 min), `docs/deck.pdf`, and a live Vercel link. The workflow's three skills: udhaar, bill (read + guided Demo Pay), Rescue Circle.
Schemes are cut. The Android code (`app/`, `demo-upi/`) is a frozen reference implementation, untested on a device.

## Panes (tmux: %0 = pane 1, %1 = pane 2; both run in ~/projects/BK and share one working folder)
- Pane 1: `web-demo/src/engine/**` + tests, README, LICENSE, CI, deck, demo script, this file.
- Pane 2: web-demo UI (`src/ui`, `App.tsx`, `public/`), sample bill + OCR json, Vercel deploy, video (ElevenLabs voiceover). Brief: `docs/PANE2_BRIEF.md`.
- Pull with `git pull --rebase --autostash origin main && git push`. Never force-push.

## Done
- Engine (TS, pure, no DOM): language layer, intents, dialogue state machine, ledger, bill parser, redactor, guided Demo Pay, Rescue Circle. 15 vitest tests drive every flow.
- CI (`.github/workflows/build.yml`): web demo tests + build, green.
- README (real vs mocked table), MIT LICENSE, `docs/deck.md` + `docs/deck.pdf` (10 slides), `docs/demo-script.md`.
- GitHub: https://github.com/snackonodin/batao-kaise (public).
- ElevenLabs key in `.env` (gitignored). No Higgsfield key yet.
- tmux restored to the original config (styled one saved as `~/.tmux.conf.styled-nov23`).

## Open
- Pane 2: UI → Vercel deploy → video. Then pane 1 adds the live URL + screenshot to the README, and the YouTube link once uploaded (the user uploads, or via Chrome if logged in).
- Final report (spec section 18): every feature marked working / mocked / fallback / untested.
