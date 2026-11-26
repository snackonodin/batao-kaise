// Intent rules. Port of app/.../intent/Intent.kt.
import { FILLERS, Normalized, SYNONYMS, findNumbers, has, hasAny, isNumberWord, wordCount } from "./lang";

export type Command = "stop" | "repeat" | "back" | "new_task" | "done";

export type Intent =
  | { kind: "bill_pay" }
  | { kind: "udhaar_add"; name: string | null; amount: number | null }
  | { kind: "udhaar_repay"; name: string | null; amount: number | null }
  | { kind: "udhaar_balance" }
  | { kind: "udhaar_remind"; name: string | null }
  | { kind: "stuck" }
  | { kind: "yes" } | { kind: "no" }
  | { kind: "cmd"; command: Command }
  | { kind: "noise" } | { kind: "unknown" };

export function describe(i: Intent): string {
  switch (i.kind) {
    case "udhaar_add": return `udhaar_add(${i.name ?? "?"}, ${i.amount ?? "?"})`;
    case "udhaar_repay": return `udhaar_repay(${i.name ?? "?"}, ${i.amount ?? "?"})`;
    case "udhaar_remind": return `udhaar_remind(${i.name ?? "?"})`;
    case "cmd": return `cmd_${i.command}`;
    default: return i.kind;
  }
}

/** Read-back line, under 15 words. Null = nothing to confirm yet. */
export function readBack(i: Intent): string | null {
  switch (i.kind) {
    case "bill_pay": return "Aap bijli ka bill bharna chahte hain. Sahi?";
    case "udhaar_add": return i.name && i.amount != null ? `${i.name}, ${i.amount} rupaye udhaar. Sahi?` : null;
    case "udhaar_repay": return i.name && i.amount != null ? `${i.name} ne ${i.amount} rupaye diye. Sahi?` : null;
    case "udhaar_balance": return "Aap sabka baaki hisaab sunna chahte hain. Sahi?";
    case "udhaar_remind": return i.name ? `Aap ${i.name} ko paise ki yaad dilana chahte hain. Sahi?` : null;
    case "stuck": return "Aap atak gaye hain. Kisi apne se madad lein?";
    default: return null;
  }
}

/** English caption for judges. */
export function caption(i: Intent): string {
  switch (i.kind) {
    case "bill_pay": return "Understood: pay the electricity bill.";
    case "udhaar_add": return `Understood: note ₹${i.amount} credit for ${i.name}.`;
    case "udhaar_repay": return `Understood: ${i.name} repaid ₹${i.amount}.`;
    case "udhaar_balance": return "Understood: read out who owes how much.";
    case "udhaar_remind": return `Understood: remind ${i.name} to pay back.`;
    case "stuck": return "Understood: the user is stuck and wants help.";
    default: return "";
  }
}

const STOP = new Set([
  "mera", "mere", "meri", "maine", "main", "mai", "mujhe", "hum", "humne", "aap", "dost", "wala", "wale", "wali",
  "ko", "ne", "se", "ka", "ki", "ke", "hai", "hain", "tha", "aaj", "kal", "abhi", "ek", "is", "us", "uska", "unka",
  "dukaan", "the", "a", "to", "from", "gave", "give", "remind", "likho", "likh", "likhna", "daal", "daalo", "add",
  "kar", "karo", "karna", "do", "dena", "liye", "liya", "lo", "sirf", "naam", "h",
]);
const CANON_KEYS = new Set(Object.keys(SYNONYMS));

const isNameTok = (tok: string, c: string) =>
  !STOP.has(tok) && c === tok && !FILLERS.has(tok) && !isNumberWord(tok) && tok.length >= 2 && /^\p{L}+$/u.test(tok) && !CANON_KEYS.has(c);

const cap = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);

/** Up to two name-like tokens right before marker ("ko"/"ne"/"se"), or the first name-like run. */
export function extractName(n: Normalized, marker?: string): string | null {
  const toks = n.tokens.map((t) => t.toLowerCase());
  if (marker) {
    for (let m = 0; m < toks.length; m++) {
      if (toks[m] !== marker) continue;
      const parts: string[] = [];
      for (let k = m - 1; k >= 0 && parts.length < 2 && isNameTok(toks[k], n.canon[k]); k--) parts.unshift(toks[k]);
      if (parts.length) return parts.map(cap).join(" ");
    }
    return null;
  }
  const start = toks.findIndex((t, i) => isNameTok(t, n.canon[i]));
  if (start < 0) return null;
  const parts = [toks[start]];
  if (start + 1 < toks.length && isNameTok(toks[start + 1], n.canon[start + 1])) parts.push(toks[start + 1]);
  return parts.map(cap).join(" ");
}

const firstAmount = (n: Normalized) => findNumbers(n.canon).find((x) => x >= 1 && x <= 10_000_000) ?? null;

export function detect(n: Normalized): Intent {
  if (!n.tokens.length) return { kind: "noise" };
  const c = n.canon;
  if (c.length <= 3) {
    if (c.includes("ruko")) return { kind: "cmd", command: "stop" };
    if (c.includes("phirse")) return { kind: "cmd", command: "repeat" };
    if (c.includes("peeche")) return { kind: "cmd", command: "back" };
    if (has(n, "naya", "kaam")) return { kind: "cmd", command: "new_task" };
    if (has(n, "ho", "gaya")) return { kind: "cmd", command: "done" };
    if (c.includes("nahi")) return { kind: "no" };
    if (c.includes("haan")) return { kind: "yes" };
  }
  if (c.includes("atak") || (hasAny(n, "madad", "help") && !hasAny(n, "udhaar", "bill"))) return { kind: "stuck" };
  if (wordCount(n) < 2) return { kind: "noise" };

  const udhaarish = c.includes("udhaar") || (c.includes("paisa") && hasAny(n, "ko", "ne", "se") && extractName(n) !== null);
  if (udhaarish || c.includes("yaad")) {
    const remind = c.includes("yaad") || (has(n, "kaise") && hasAny(n, "loon", "maangu")) || c.includes("loon");
    if (remind) return { kind: "udhaar_remind", name: extractName(n, "se") ?? extractName(n, "ko") ?? extractName(n) };
    if (hasAny(n, "kiska", "kitna", "hisaab", "hisab", "list", "sab", "sabka")) {
      if (extractName(n) === null || hasAny(n, "kiska", "sabka", "sab")) return { kind: "udhaar_balance" };
    }
    if (c.includes("ne") && c.includes("diye")) return { kind: "udhaar_repay", name: extractName(n, "ne"), amount: firstAmount(n) };
    return { kind: "udhaar_add", name: extractName(n, "ko") ?? extractName(n), amount: firstAmount(n) };
  }
  if (c.includes("ne") && c.includes("diye") && extractName(n, "ne") && firstAmount(n) !== null)
    return { kind: "udhaar_repay", name: extractName(n, "ne"), amount: firstAmount(n) };
  if (hasAny(n, "bill", "bijli")) return { kind: "bill_pay" };
  return { kind: "unknown" };
}
