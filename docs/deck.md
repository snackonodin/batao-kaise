---
marp: true
paginate: true
size: 16:9
style: |
  @import url('https://fonts.googleapis.com/css2?family=Mukta:wght@400;600;800&display=swap');
  :root { --green: #1F5E3B; --ink: #1B1B18; --muted: #5B5A55; --soft: #E3EFE7; --bg: #FBF7F0; --amber: #F2B33D; }
  section { font-family: 'Mukta', sans-serif; background: var(--bg); color: var(--ink); padding: 56px 72px; font-size: 27px; line-height: 1.35; }
  h1 { color: var(--green); font-size: 54px; font-weight: 800; margin: 0 0 18px; line-height: 1.1; }
  h2 { color: var(--green); font-size: 40px; font-weight: 800; margin: 0 0 22px; }
  strong { color: var(--green); }
  em { color: var(--muted); font-style: normal; }
  section::after { color: var(--muted); font-size: 16px; }
  .big { font-size: 96px; font-weight: 800; color: var(--green); line-height: 1; }
  .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 40px; }
  .card { background: #fff; border: 2px solid #E4DED3; border-radius: 18px; padding: 20px 24px; }
  .flow { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; font-size: 24px; }
  .flow span { background: var(--soft); border-radius: 12px; padding: 10px 14px; font-weight: 600; }
  .flow b { color: var(--green); font-size: 28px; }
  .tag { display: inline-block; background: var(--amber); color: #3d2a00; border-radius: 8px; padding: 2px 10px; font-size: 18px; font-weight: 800; }
  .hi { font-size: 30px; font-weight: 600; }
  table { font-size: 22px; border-collapse: collapse; }
  th { background: var(--green); color: #fff; }
  td, th { padding: 8px 12px; }
  section.title { background: var(--green); color: #fff; justify-content: center; }
  section.title h1 { color: #fff; font-size: 84px; }
  section.title p { color: #E3EFE7; font-size: 32px; }
---

<!-- _class: title -->
<!-- _paginate: false -->

# Batao Kaise

**Speak, and your phone shows you how.**

A voice-first helper for rural India. One button, Hindi or Hinglish, and it never taps for you.

---

## The problem: phones everywhere, skills missing

<div class="cols">
<div>
<div class="big">21.9%</div>

computer literacy in rural India
<em>IHDS-III</em>
</div>
<div>
<div class="big">20.4%</div>

of households that use digital services need outside help to do so
<em>NCAER</em>
</div>
</div>

Paying a bill, keeping udhaar, filling a form: today these mean **typing, tapping and reading English menus**. People ask a neighbour, or they don't do it.

---

## Who it's for

<div class="cols">
<div class="card">

**Elders** who own a smartphone but use only calls

**Daily-wage workers** and **homemakers** paying bills and recharges

</div>
<div class="card">

**Small shopkeepers** who still keep udhaar in a notebook

Hindi and Hinglish speakers with Bhojpuri, Awadhi, Haryanvi and Marwari accents, mixing in "OTP", "UPI" and "bill"

</div>
</div>

Many read little, so it's **voice first, big visuals second, text last**.

---

## One workflow, done by speaking

<div class="flow"><span>🎤 Tap</span><b>→</b><span>"Batao, kya karna hai?"</span><b>→</b><span>You speak</span><b>→</b><span>"…Sahi?"</span><b>→</b><span>✅ Haan</span><b>→</b><span>Step by step</span></div>

<br>

| You say | It does |
|---|---|
| "Ramesh ko do sau udhaar" | "Ramesh Kumar ya Ramesh Yadav?" → reads back → saves. "Kiska kitna baaki?" reads the balances aloud |
| "Ramesh se paisa kaise loon?" | Drafts a polite WhatsApp reminder with a UPI QR. **You** press Send |
| "Bijli ka bill bharna hai" | Reads the bill aloud, then guides the payment |
| "Atak gaya" | Sends a redacted summary to someone you trust |

Nothing happens before **Haan**. Amounts over ₹2,000 are checked twice.

---

## The screen guide: it points, you press

<div class="cols">
<div>

1. Reads the bill: account number **digit by digit**, amount, due date. Unclear fields are marked **"Saaf nahi"**, never guessed.
2. Opens the payment app and **highlights the exact button**: Bills → Electricity → account → Pay.
3. On a wrong screen: *"Ye galat screen hai, peeche jaaiye."*
4. On PIN and OTP screens it steps back: *"Ye aap khud daaliye."*

</div>
<div class="card">

**Never taps, types or swipes for the user.** The goal is confidence: people learn to do it themselves.

Android: an Accessibility overlay that is read-only and non-touchable, running only during a task. Instructions come from **reviewed, versioned journeys**, never from an LLM.

</div>
</div>

---

## Rescue Circle: when it can't help

<div class="cols">
<div>

Triggered by **"atak gaya"**, a step failing twice, or an unknown request.

It builds a summary from the **task state, not a screenshot**: goal, app, screen, failed step, attempts.

Reads it aloud → you can edit → **"Bhej doon?"** → you share it on WhatsApp, or as a ready question for Gemini or Claude.

</div>
<div class="card">

Madad chahiye
Kaam: Bijli ka bill bharna
Screen: Electricity
Account: **[ACCOUNT HATAYA]**
OTP **[OTP HATAYA]**

Removed: OTPs, PINs, passwords, and account, Aadhaar, phone and UPI numbers. It redacts again after every edit.

</div>
</div>

---

## What it depends on

| Dependency | Why | Risk and plan |
|---|---|---|
| Speech-to-text: **Wispr Flow** (Hinglish), Android or browser hi-IN | Understanding mixed speech | Works with no keys; we always read back, never guess |
| **Android Accessibility** + Google Play policy | The on-screen guide | Read-only, non-touchable, consent first; falls back to spoken steps |
| **ML Kit** on-device OCR | Reading bills | The photo never leaves the phone |
| **WhatsApp**, **UPI apps** | Reminders, payments | The user sends and pays; guided via a dummy app in the demo |
| **Reviewed journeys** | Every instruction | Versioned JSON with reviewer and date; the LLM only detects intent |

---

## Safety and privacy

- **Consent before any data leaves the phone.** No analytics SDKs and no accounts.
- **Audio is never stored.** The PIN is never stored. OTPs are always typed by the user.
- **Read-back for every action**, a second check over ₹2,000, and picture buttons after repeated failures.
- **No guessing:** unclear bill fields are marked "Saaf nahi". Similar names are asked about.
- **No health features.** No money moves through us. Demo Pay is labelled "DEMO, no real money".

---

## Launch plan

<div class="cols">
<div class="card">

**30-day pilot** <span class="tag">TARGETS</span>

- **50 users** in **two districts**
- Target: **70% of tasks done without help**
- Every Rescue Circle case is reviewed and turned into a new verified journey

</div>
<div class="card">

**Then: scale through people they already trust**

- **Common Service Centres (CSCs)**: onboarding and verified journeys
- **Kirana networks**: udhaar and reminders as the entry point
- More journeys: recharge, gas booking, ration

</div>
</div>

<em>Pilot figures are targets, not results.</em>

---

## The ask

<div class="cols">
<div>

- **Pilot partners** in two districts: CSC operators and kirana associations
- **Wispr Flow API access** for Hinglish streaming speech
- **Journey reviewers** who know local apps and dialects

</div>
<div class="card">

**Try it:** the live web demo and code on GitHub

github.com/snackonodin/batao-kaise

<span class="hi">Apne phone par, khud se.</span>

</div>
</div>
