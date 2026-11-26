# Batao Kaise: demo video script (2:55)

Footage: the **web demo** (phone-frame simulation on Vercel), because no Android device is available for recording.
A lower-third stays on screen the whole time: **"Web demo simulating the Android app. The real app is in the GitHub repo."**
Optional 4-second Higgsfield intro clip: captioned **"Illustrative"**.

Voices (ElevenLabs): **Narrator** (calm Hindi/English mix, for judges) and **User** (an older shopkeeper's voice saying the commands).
App replies use the app's own lines exactly as written below (they come from the code and journeys).
Captions: English, bottom of screen, max 2 lines.

| Time | Screen | Audio | English caption |
|---|---|---|---|
| 0:00–0:08 | (Optional illustrative clip) a shopkeeper looking at a phone, unsure | Narrator: "Gaon mein sabke paas phone hai. Par use karna har koi nahi jaanta." | Everyone has a phone. Not everyone can use it fully. |
| 0:08–0:15 | Web demo home, big green mic. Cursor taps the mic. | App: "Batao, kya karna hai?" | One button. Speak in Hindi or Hinglish. |
| 0:15–0:22 | User speaks; live text "bijli ka bill bharna hai" | User: "Bijli ka bill bharna hai." App: "Aap bijli ka bill bharna chahte hain. Sahi?" User: "Haan." | It always reads back what it heard. Nothing happens before "Haan". |
| 0:22–0:38 | Demo bill appears; coloured boxes: Account no. (blue), Rakam (green), Aakhri tareekh (orange) | App: "Upar daayein aapka account number hai: 4 5 1 2 7 7 8 8 3 3 0 1." "Rakam hai: 1240 rupaye." "Aakhri tareekh hai: 15 December 2026." | On-device OCR reads the bill aloud. Numbers digit by digit. Unclear fields are marked "Saaf nahi", never guessed. |
| 0:38–0:55 | Mock Demo Pay. Highlight moves Bills → Electricity → account field → Pay. Cursor (the user) taps each. PIN screen: highlight disappears. | App: "Neeche 'Bills' dabaiye." "Ab 'Electricity' dabaiye." "Is dabbe mein account number likhiye…" "Rakam 1240 rupaye hai. Sahi ho to 'Pay' dabaiye." "Ye aap khud daaliye. PIN kisi ko mat bataiye." "Ho gaya! Aapne khud kiya, shabaash!" | It highlights the exact button. It never taps for you. On PIN and OTP screens it steps back. |
| 0:55–1:10 | Udhaar | User: "Ramesh ko do sau udhaar." App: "Ramesh Kumar ya Ramesh Yadav?" User: "Kumar." App: "Ramesh Kumar, 200 rupaye udhaar. Sahi?" User: "Haan." | A voice udhaar notebook, encrypted on the phone. Similar names are checked. |
| 1:10–1:30 | Reminder draft with UPI QR; share sheet mock with WhatsApp; highlight on Send | User: "Ramesh se paisa kaise loon?" App: "Ramesh Kumar ke 400 rupaye baaki hain. WhatsApp par yaad dilaun?" User: "Haan." App: "Hare 'Send' button ko dabaiye." | A polite reminder with a UPI QR, sent by the user on WhatsApp. |
| 1:30–1:45 | Noisy retry: background noise sound, transcript "umm…" | App: "Thoda shor hai. Phone munh ke paas laakar dobara boliye." (second failure) picture buttons appear: Bill, Udhaar, Yojana, Phone madad | Noise? It asks again, then shows picture buttons. |
| 1:45–2:00 | Wrong name: transcript shows "Rakesh ko sau udhaar" | App: "Rakesh, 100 rupaye udhaar. Sahi?" User: "Nahi." App: "Achha, phir se bataiye." User: "Sirf naam: Ramesh Yadav." | Misheard? Say "Nahi". It asks for just the name. |
| 2:00–2:35 | Rescue Circle: user says "atak gaya"; summary card with [ACCOUNT HATAYA], [OTP HATAYA] tags; "Bhej doon?"; WhatsApp share to "Beta (Rahul)" | User: "Main atak gaya." App: "Aap atak gaye hain. Kisi apne se madad lein?" User: "Haan." App reads the summary. App: "Bhej doon?" User: "Haan." | When stuck, it sends a clear summary to someone you trust. OTPs, PINs, account, Aadhaar, phone and UPI numbers are removed first. |
| 2:35–2:50 | Schemes: voice questions (age, land, income), results with "shayad mil sakti hai" and the "demo, verify on official site" label | App: "Ye yojana shayad mil sakti hai." | Likely schemes, the documents needed, and the nearest CSC. Never "you are eligible". |
| 2:50–2:55 | Logo, GitHub URL, "Batao Kaise" | Narrator: "Batao Kaise. Apne phone par, khud se." | Confidence: people learn to do it on their own phone, by themselves. |

## Honesty rules for the edit
- The lower-third "Web demo simulating the Android app" stays visible throughout the app footage.
- No AI-generated footage of the app UI. AI clips only for the intro, captioned "Illustrative".
- Fake data only: the bill, ledger names and every number are fake.
