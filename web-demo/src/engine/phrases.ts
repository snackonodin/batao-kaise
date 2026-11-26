// 20 typed test phrases: accents, code-mixing, noise words, wrong names. Shown on the debug panel and run in tests.
import { normalize } from "./lang";
import { describe, detect } from "./intent";

export const TEST_PHRASES: { text: string; expect: string; note: string }[] = [
  { text: "Ramesh ko do sau udhaar", expect: "udhaar_add(Ramesh, 200)", note: "basic Hinglish" },
  { text: "रमेश को दो सौ उधार", expect: "udhaar_add(Ramesh, 200)", note: "Devanagari from hi-IN recognizer" },
  { text: "arre bhai ramesh ko dhai sau rupaye udhari likh do", expect: "udhaar_add(Ramesh, 250)", note: "fillers + synonym udhari" },
  { text: "Suresh Yadav ko 1500 karja", expect: "udhaar_add(Suresh Yadav, 1500)", note: "two-word name, karja" },
  { text: "mohan ko saade teen sau baaki", expect: "udhaar_add(Mohan, 350)", note: "saade + baaki" },
  { text: "Ramesh ne 100 de diye", expect: "udhaar_repay(Ramesh, 100)", note: "repayment" },
  { text: "रमेश ने सौ रुपये लौटा दिए", expect: "udhaar_repay(Ramesh, 100)", note: "Devanagari repayment, lauta" },
  { text: "Kiska kitna baaki hai?", expect: "udhaar_balance", note: "balance query" },
  { text: "Ramesh se paisa kaise loon?", expect: "udhaar_remind(Ramesh)", note: "remind" },
  { text: "ramesh ko yaad dilao paise ki", expect: "udhaar_remind(Ramesh)", note: "remind, yaad" },
  { text: "bijli ka bill bharna hai", expect: "bill_pay", note: "bill" },
  { text: "light ka bil bharna h", expect: "bill_pay", note: "Bhojpuri-style light/bil, SMS spelling h" },
  { text: "बिजली का बिल भरना है", expect: "bill_pay", note: "Devanagari bill" },
  { text: "electricity bill pay karna hai", expect: "bill_pay", note: "English code-mix" },
  { text: "Rakesh ko sau udhaar", expect: "udhaar_add(Rakesh, 100)", note: "misheard name (corrected by Nahi)" },
  { text: "main atak gaya", expect: "stuck", note: "stuck" },
  { text: "umm", expect: "noise", note: "noise, 1 word" },
  { text: "haan ji", expect: "yes", note: "yes" },
  { text: "phir se", expect: "cmd_repeat", note: "global repeat" },
  { text: "mausam kaisa hai aaj", expect: "unknown", note: "unknown request -> Rescue" },
];

export const runPhrases = () =>
  TEST_PHRASES.map((p) => { const got = describe(detect(normalize(p.text))); return { ...p, got, pass: got === p.expect }; });
