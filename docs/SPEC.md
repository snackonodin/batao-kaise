# Batao Kaise: final build spec (Round 2)

Decisions made with the user (2026-11-27):
- The web demo deploys to **Vercel** (not GitHub Pages).
- Higgsfield: the user supplies an API key/MCP. Use it only for illustrative intro/outro clips, captioned as such.
- Two Claude panes: pane 1 = Android (`app/`, `demo-upi/`, Gradle). Pane 2 = everything else (see PANE2_BRIEF.md).

## 1. What we're building
Batao Kaise is an Android app that helps rural Indians use their phones by voice. The user presses one button and says what they need in Hindi or Hinglish. The app confirms what it heard, then guides them through the task: it reads bills aloud, opens the right app, and highlights the exact button to press on screen. It never taps for them. When it can't help, the Rescue Circle sends a clear, redacted summary to someone they trust. The goal is confidence: people learn to do things on their own phone, by themselves.

## 2. What we must submit
- Code on GitHub: public, and it must build and run with no secret keys.
- A YouTube demo of up to 3 minutes showing the product working.
- A pitch deck PDF covering the problem, users, what the product depends on, and the launch plan.
- Optional live link: a small web demo for a bonus, if it works.

## 3. Users
People who own a phone but can't use it to the full: elders, daily-wage workers, homemakers and small shopkeepers who still keep udhaar in a notebook. They speak Hindi and Hinglish with Bhojpuri, Awadhi, Haryanvi or Marwari accents, mixing in English words like OTP, UPI and bill. Many read little, so voice and big visuals come first and text second.

## 4. Repo and project setup
- One Gradle project with two Android app modules:
  - `app`: Batao Kaise itself, in Kotlin and Jetpack Compose.
  - `demo-upi`: a separate dummy UPI app ("Demo Pay") with Home, Bills, Electricity, an account-number field and a Pay screen. Batao Kaise guides this app in the demo, so we never depend on a real bank app. Label every screen "DEMO, no real money".
- Optional `backend/` folder (FastAPI) for Wispr Flow tokens and LLM intent calls.
- Optional `web-demo/` folder for the live link (section 13).
- `assets/demo/`: a sample electricity bill image with fake data, demo journeys, a demo scheme list and a seeded ledger.
- `README.md`: what it is, a screenshot, setup in 5 steps, how to enable permissions, how to run the demo, what's real and what's mocked, and the license (MIT).
- `.env.example` with empty values only. `.gitignore` for .env, local.properties and keystores. Never commit keys.
- A GitHub Actions workflow that builds both debug APKs. Attach the APKs to a GitHub Release so judges can install them.

## 5. Runs with no keys
- On first launch with no keys, the app runs in Demo Mode, and every feature in the demo video works.
- Speech-to-text: Android SpeechRecognizer (hi-IN). Intent detection: keyword and regex rules. Text-to-speech: Android TextToSpeech.
- If keys are added, switch on Wispr Flow (section 6) and LLM intent detection. A Settings screen shows which engine is active ("Voice: Wispr Flow" or "Voice: Android (demo)").

## 6. Voice layer
- One `SpeechInput` interface with three engines:
  - Wispr Flow WebSocket API, with Hinglish selected, when API access is approved. The backend issues a short-lived client token. The app streams 16 kHz mono 16-bit PCM as equal base64 WAV chunks of about 1 second, sending auth, append and commit messages. Read the current docs at api-docs.wisprflow.ai before writing this.
  - Wispr Flow app dictation: the user dictates into our focused text box using the installed Flow app.
  - Android SpeechRecognizer, labelled "not Wispr".
- Normalize every transcript with a synonym list (udhaar = udhari, baaki, karja; bijli = bijuli, light; paisa = rupaye) and convert spoken numbers ("do sau" = 200, "dhai sau" = 250, "two hundred" = 200).
- Replies use TextToSpeech (hi-IN) at 0.9 speed, shown as large Hinglish text in a floating bubble above any app. "Phir se" repeats more slowly. Keep each spoken line under 15 words.
- Listening states: Idle, Listening (pulsing ring and live text), Processing, Speaking. Stop after 1.5 s of silence once speech starts. The user's speech interrupts the app.
- Commands anywhere: "ruko" (stop), "phir se" (repeat), "peeche" (back), "naya kaam" (start over).
- Never store raw audio.

## 7. Core loop
1. The user taps the floating mic, which stays over any app. App: "Batao, kya karna hai?"
2. The user speaks; live text shows.
3. Read-back. App: "Aap bijli ka bill bharna chahte hain. Sahi?" Big Haan and Nahi buttons appear as well as voice. Nothing happens before Haan.
4. Match a journey and guide one step at a time, asking "Ho gaya?"
5. Always repeat back names, amounts, dates and numbers. Amounts over ₹2,000 get a second check: "Do hazaar se zyada hai. Pakka?"

## 8. Screen guide (Dikhao)
- An AccessibilityService, enabled after a spoken explanation and consent, reads the current app's view tree only while a journey is running.
- Each journey step names the expected app package, a screen marker and the target element (view ID, then text, then content description).
- Draw a non-touchable TYPE_ACCESSIBILITY_OVERLAY: a pulsing outline around the target, an arrow, and the bubble ("Neeche 'Electricity' dabaiye"). Recompute position on scroll and window-change events.
- When the next expected screen appears, advance automatically and speak the next step. On a wrong screen: "Ye galat screen hai, peeche jaaiye" and point to Back.
- Never tap, type or swipe for the user. On OTP, PIN or password screens, hide the overlay and say "Ye aap khud daaliye."
- If the element isn't found, use spoken steps only.
- Build and test the guide against demo-upi first.

## 9. Journeys
- Versioned JSON: id, title, trigger phrases, app package, reviewed_by, review_date, status (verified or demo), and steps.
- Each step: say, expect_screen, target, action_hint (tap, type, scroll) and on_fail.
- The LLM, when enabled, only returns intent and details as JSON; it never writes steps.
- Ship: pay a bill in Demo Pay; send an udhaar reminder on WhatsApp; fill one scheme form on a demo web page.

## 10. Features
### 10a. Read and pay a bill
CameraX photo, or the sample bill from Demo Mode's gallery. On-device ML Kit text recognition (Devanagari and Latin) with bounding boxes. Templates find account/consumer number, amount and due date. Show the photo with labelled coloured boxes (Account no., Rakam, Aakhri tareekh) and read each: "Upar daayein aapka account number hai: 4-5-1-2…". Numbers read digit by digit; tapping a box repeats it. Unreadable fields get a grey "Saaf nahi" box; never guess. App: "Bharne ka tarika bataun?" On Haan, open Demo Pay. Guide highlights Bills, then Electricity, then the account field (saying the number again), then Pay. The user types and taps everything.
### 10b. Udhaar log and money reminders
"Ramesh ko do sau udhaar" → "Ramesh, 200 rupaye udhaar. Sahi?" → saved to an encrypted Room ledger. "Ramesh ne 100 de diye" records a repayment after confirmation. "Kiska kitna baaki hai?" reads the top five balances. "Ramesh se paisa kaise loon?" → "Ramesh ke 200 rupaye baaki hain. WhatsApp par yaad dilaun?" Drafts a polite reminder with a UPI QR (ZXing, from the shopkeeper's UPI ID) and opens the WhatsApp share sheet. Guide points to Send. Ledger screen: large names and balances, edit, undo, delete, locked by a 4-digit PIN.
### 10c. Find and apply for schemes
Ask age, land, income, family and state by voice. Deterministic rules over a demo scheme JSON; suggest likely schemes ("shayad mil sakti hai", never "aap eligible hain"), documents needed and nearest CSC. App: "Abhi apply karein?" Open the form in a Chrome Custom Tab; Demo Mode uses a bundled demo form page. Guide highlights each field and says what to enter. User types everything and enters their own OTPs. Label all scheme data "demo, verify on official site".
### 10d. Rescue Circle
Triggered by "atak gaya", a step failing twice, or no journey match. Summary from journey state, not a screenshot: goal, app, screen, failed step and attempts. Redact OTPs, PINs, passwords, and account, Aadhaar, phone and UPI numbers. Read aloud, allow edits, redact again after any edit. "Bhej doon?" Share on WhatsApp to a trusted person, or as a ready question to Gemini or Claude. Save the case for later conversion into a reviewed journey.

## 11. When things go wrong
- Noise / <2 words: "Thoda shor hai. Phone munh ke paas laakar dobara boliye." After a second failure: four picture buttons: Bill, Udhaar, Yojana, Phone madad.
- Silence: after 6 s "Main sun raha hoon."; after 12 s stop listening.
- Accents / mixed language: never guess; always read back; use the synonym list.
- Misheard intent: "Achha, phir se bataiye." After two Nahis, show picture buttons.
- Misheard name/amount: ask for just the name, then just the amount. Similar names: "Ramesh Kumar ya Ramesh Yadav?"
- Unknown request: "Ye main abhi nahi seekha. Kisi apne se madad maangein?" then Rescue Circle.
- No internet: say so; udhaar and bill reading still work offline.
- Accessibility denied: "Koi baat nahi, main bolkar bataunga." then spoken steps only.
- App not installed: "Ye app phone mein nahi hai."

## 12. Onboarding and design
- First launch, all by voice: greeting, then each permission explained in one line, in order (mic, notifications, overlay, Accessibility). Works if any is refused. End with a practice run on Demo Pay.
- Design: warm off-white background, one deep green accent, Mukta font (Devanagari + Latin), buttons ≥ 56 dp, labelled icons, one main action per screen, high contrast.
- Android 8+, 2 GB RAM phones; APK < 30 MB; cold start < 3 s.
- No health features. Consent before any data leaves the phone. No analytics SDKs.

## 13. Optional live link
web-demo/ SPA (Vite + React), deployed (to Vercel, per user). Simulates the phone: mic via Web Speech API (hi-IN) with a "type instead" box. Covers the core loop, bill reader with highlight boxes on the sample bill, udhaar with reminder draft, a mock Demo Pay screen with the guide overlay, and Rescue Circle. Label: "Web demo; the real app is Android".

## 14. Build order
1. Repo, both modules, Demo Mode, voice loop with read-back, TTS bubble.
2. Journey engine, Accessibility overlay guiding Demo Pay end to end.
3. Bill reader with boxes, linked to the Demo Pay journey.
4. Udhaar ledger, reminder and QR, plus Rescue Circle with redaction.
5. Schemes on the demo form, error handling, onboarding, README, CI and Release.
6. Optional: web demo and Wispr Flow API engine.

## 15. Tests
Unit tests for the number parser, synonym normalizer, redactor and journey matcher. A debug screen that runs 20 typed test phrases (accents, code-mixing, noise words, wrong names) and shows pass/fail. Test redaction with fake OTP, Aadhaar and account numbers only.

## 16. Demo video script (docs/demo-script.md, < 3:00)
- 0:00–0:15 Problem in one line, then press the mic.
- 0:15–0:55 Bill photo with boxes read aloud, then Demo Pay with the highlight moving Bills → Electricity → account field → Pay.
- 0:55–1:30 "Ramesh ko do sau udhaar", then the reminder with QR in WhatsApp share.
- 1:30–2:00 A noisy retry, then a wrong-name correction.
- 2:00–2:35 "Atak gaya", then the Rescue Circle summary with redactions, sent on WhatsApp.
- 2:35–3:00 A scheme suggestion, and the closing line about confidence.
- English on-screen captions for judges.

## 17. Pitch deck (docs/deck.md, Marp → docs/deck.pdf, 10 slides)
Title; the problem (phones everywhere, skills missing; cite IHDS-III 21.9% rural computer literacy and NCAER's finding that 20.4% of households using digital services need outside help); users; the voice flow; the screen guide; Rescue Circle; what it depends on (Wispr Flow, Android Accessibility and Google Play policy, ML Kit, WhatsApp, UPI apps, reviewed journeys); safety and privacy; the launch plan (30-day pilot, 50 users, two districts, target 70% of tasks done without help, then partnering with CSCs and kirana networks); the ask. Use only these numbers; mark pilot figures as targets.

## 18. Final report
List every feature as working, mocked, fallback or untested. Never present a mock as real.
