// Removes OTPs, PINs, passwords, and account, Aadhaar, card, phone and UPI numbers.
// Port of app/.../rescue/Redactor.kt. Runs on every summary and again after every edit.

const RULES: [string, RegExp, boolean][] = [
  ["OTP", /(otp|one[- ]?time|code|ओटीपी|कोड)(\s*(?:hai|is|:|=|-|h)?\s*)([0-9]{3,8})/gi, true],
  ["PIN", /(\bm?pin\b|upi pin|पिन)(\s*(?:hai|is|:|=|-|h|number)?\s*)([0-9]{3,8})/gi, true],
  ["PASSWORD", /(password|passcode|pass word|पासवर्ड)(\s*(?:hai|is|:|=|-|h)?\s*)(\S+)/gi, true],
  ["UPI", /\b[\w.\-]{2,}@[a-z][a-z0-9.\-]{1,}\b/gi, false],
  ["PHONE", /(?<!\d)(?:\+?91[\s-]?|0)?[6-9]\d{4}[\s-]?\d{5}(?!\d)/g, false],
  ["NUMBER", /(?<!\d)\d{4}[\s-]?\d{4}[\s-]?\d{4}(?:[\s-]?\d{4})?(?!\d)/g, false],
  ["ACCOUNT", /(?<!\d)\d(?:[\s-]?\d){7,17}(?!\d)/g, false],
];

export interface Redacted { text: string; removed: Record<string, number> }

export function redact(input: string): Redacted {
  let s = input;
  const removed: Record<string, number> = {};
  for (const [kind, re, keepKeyword] of RULES) {
    s = s.replace(re, (...m: string[]) => {
      removed[kind] = (removed[kind] ?? 0) + 1;
      const tag = `[${kind} HATAYA]`;
      return keepKeyword ? m[1] + m[2] + tag : tag;
    });
  }
  return { text: s, removed };
}
