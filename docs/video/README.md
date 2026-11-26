# Demo video

`batao-kaise-demo.mp4`: 2:59, 1920x1080, recorded from the real web demo (production build), not mocked up.

- Footage: `scripts/video/record.mjs` runs `docs/demo-script.md` scene by scene in headless Chrome. The "user" speaks through
  the engine's own speech inputs (live transcript, then the final transcript), and taps are real clicks shown by a touch dot.
- Voices: ElevenLabs `eleven_v4`, Hindi (`language_code: hi`). App = Bella, shopkeeper = Bill, narrator = George
  (premade voices). Every app line is the engine's exact text; each is held on screen exactly as long as its audio.
- Captions and the lower-third "Web demo simulating the Android app" are drawn in the page while recording.
  No AI-generated footage. The planned "Illustrative" intro clip is replaced by a plain title card (no Higgsfield key).
- Changes from the script: schemes are cut (out of scope). The misheard name and the similar-name check are one udhaar
  scene ("Rakesh" → Nahi → "Ramesh" → "Ramesh Kumar ya Ramesh Yadav?"). "Atak gaya" is reached from the picture buttons
  after the noise retry.

Rebuild (needs `ELEVENLABS_API_KEY` in the repo's `.env`; clips are cached in `scripts/video/out/audio`):

```sh
cd web-demo && npm run build && npx vite preview --port 5180 &
cd scripts/video && npm ci
node record.mjs collect   # every line the app says
node record.mjs           # timed recording + timeline
node build.mjs            # mix voices, encode docs/video/batao-kaise-demo.mp4
```
