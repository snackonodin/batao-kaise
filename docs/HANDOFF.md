# Handoff between panes

## Contracts from pane 1 (Android) for pane 2
### demo_form.html (assets/demo/ and web-demo/public/)
The app's journey `app/src/main/assets/journeys/demo_scheme_form.json` guides this page. Keep these exactly:
- Page heading text: **"Demo Yojana Form"** (page 1 only), plus a "DEMO, verify on official site" banner.
- Page 1 inputs (each needs this `id`, a visible `<label>` and the same text as `placeholder`):
  - `full_name`, label/placeholder "Poora naam"
  - `age`, label/placeholder "Umar" (type=number)
  - `mobile`, label/placeholder "Mobile number" (type=tel)
  - button `send_otp_btn`, text "OTP bhejein": hides page 1 and shows page 2
- Page 2 heading text: **"OTP daaliye"**, input `otp` (type=password, autocomplete=one-time-code), button `submit_btn` text "Jama karein"
- Page 3 heading text: **"Form jama ho gaya"**, with "Ye demo tha, koi data kahin nahi gaya."
- No network calls. Nothing is stored.
- It must be served over https (Chrome Custom Tab). The app's default URL is `https://batao-kaise.vercel.app/demo_form.html`; tell pane 1 the real Vercel URL here once deployed.

### sample_bill.png
Account `4512 7788 3301` (Demo Pay accepts only `451277883301`), amount `₹1,240`, due `15/12/2026`, name "Sunita Devi".
Labels the OCR templates look for: "Account No" / "Consumer No" / "खाता संख्या", "Amount Payable" / "देय राशि", "Due Date" / "अंतिम तिथि".

## Questions for pane 1
(append here)

## Pane 2 status (2026-11-28, night)
- **BLOCKED on Vercel login (needs the user).** `npx vercel whoami` says no credentials. To unblock, run in pane 2:
  `! cd web-demo && npx vercel login`, then tell pane 2 "deploy". Pane 2 then runs `npx vercel --prod` from `web-demo/`
  and writes the URL here. Everything else (UI, video) continues without it; the video is recorded from a local
  production build (`vite preview`), which is the same bundle Vercel would serve.
- **For pane 1 (engine, from the design critique):** (1) clear the old view when a new task starts (questions currently render over
  Demo Pay success / sent chat); (2) read the account number in groups of 4 like the bill prints it; (3) rescue summary from
  "atak gaya" repeats Kaam/Kaaran with no content; (4) confirm new names explicitly ("Rakesh naya naam hai. Likh doon?").
