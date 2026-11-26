// Bill templates over OCR lines. Port of app/.../bill/BillParser.kt.
// Android runs ML Kit on-device; the web demo uses precomputed OCR lines for the sample bill.

export interface OcrLine { text: string; l: number; t: number; r: number; b: number }
export type FieldKind = "account" | "amount" | "due";
export const FIELD_LABEL: Record<FieldKind, string> = { account: "Account no.", amount: "Rakam", due: "Aakhri tareekh" };
export const FIELD_COLOR: Record<FieldKind, string> = { account: "#1E5AA8", amount: "#1F5E3B", due: "#C0661A" };

/** value null = unreadable: grey "Saaf nahi" box, never guessed. */
export interface BillField { kind: FieldKind; value: string | null; box: [number, number, number, number] | null }

const ACCOUNT_LABEL = /(account|a\/c|consumer|ca\s*no|k\.?\s*no|service\s*no|खाता|उपभोक्ता|कंज्यूमर)/i;
const AMOUNT_LABEL = /(amount\s*payable|net\s*payable|payable|total\s*amount|bill\s*amount|amount\s*due|देय\s*राशि|कुल\s*राशि|राशि|रकम)/i;
const DUE_LABEL = /(due\s*date|last\s*date|pay\s*by|अंतिम\s*तिथि|देय\s*तिथि|आखिरी\s*तारीख|अंतिम\s*तारीख)/i;
const ACCOUNT_VALUE = /(?<!\d)(\d[\d ]{7,18}\d)(?!\d)/;
const AMOUNT_VALUE = /(?:₹|rs\.?|inr|रु\.?)?\s*(\d{1,3}(?:,\d{2,3})+|\d{2,7})(?:\.\d{1,2})?/gi;
const DATE_VALUE = /(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})|(\d{1,2})\s*(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[ ,\-]*(\d{2,4})/i;

const box = (o: OcrLine): [number, number, number, number] => [o.l, o.t, o.r, o.b];

function find(lines: OcrLine[], kind: FieldKind, label: RegExp, value: (s: string) => string | null): BillField {
  const lab = lines.find((l) => label.test(l.text));
  if (!lab) return { kind, value: null, box: null };
  const m = lab.text.match(label)!;
  const after = lab.text.slice((m.index ?? 0) + m[0].length);
  const v = value(after);
  if (v) return { kind, value: v, box: box(lab) };
  const h = Math.max(10, lab.b - lab.t);
  const cy = (o: OcrLine) => (o.t + o.b) / 2;
  const cands = lines.filter((o) => o !== lab).filter((o) => {
    const sameRow = Math.abs(cy(o) - cy(lab)) < h && o.l >= lab.l;
    const below = o.t >= lab.t && o.t - lab.b < h * 3 && o.r > lab.l - h * 2 && o.l < lab.r + h * 6;
    return sameRow || below;
  }).sort((a, b) => (Math.abs(cy(a) - cy(lab)) * 3 + Math.abs(a.l - lab.r)) - (Math.abs(cy(b) - cy(lab)) * 3 + Math.abs(b.l - lab.r)));
  for (const c of cands) {
    const cv = value(c.text);
    if (cv) return { kind, value: cv, box: [Math.min(lab.l, c.l), Math.min(lab.t, c.t), Math.max(lab.r, c.r), Math.max(lab.b, c.b)] };
  }
  return { kind, value: null, box: box(lab) };
}

export function parseBill(lines: OcrLine[]): Record<FieldKind, BillField> {
  return {
    account: find(lines, "account", ACCOUNT_LABEL, (s) => {
      const d = s.match(ACCOUNT_VALUE)?.[1]?.replace(/\D/g, "");
      return d && d.length >= 8 && d.length <= 18 ? d : null;
    }),
    amount: find(lines, "amount", AMOUNT_LABEL, (s) => {
      if (DATE_VALUE.test(s)) return null;
      for (const m of s.matchAll(AMOUNT_VALUE)) { const d = m[1].replace(/\D/g, ""); if (d.length >= 2 && d.length <= 7) return m[1]; }
      return null;
    }),
    due: find(lines, "due", DUE_LABEL, (s) => s.match(DATE_VALUE)?.[0]?.trim() ?? null),
  };
}

const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"];
export function spokenDate(s: string): string {
  const m = s.match(/(\d{1,2})[/.\-](\d{1,2})[/.\-](\d{2,4})/);
  if (!m) return s;
  const name = MONTHS[parseInt(m[2]) - 1];
  if (!name) return s;
  return `${parseInt(m[1])} ${name} ${m[3].length === 2 ? "20" + m[3] : m[3]}`;
}

export function position(b: [number, number, number, number], w: number, h: number): string {
  const cx = (b[0] + b[2]) / 2 / w, cy = (b[1] + b[3]) / 2 / h;
  const v = cy < 0.36 ? "Upar" : cy > 0.66 ? "Neeche" : "Beech mein";
  const hz = cx < 0.38 ? "baayein" : cx > 0.62 ? "daayein" : "";
  if (v === "Beech mein") return hz ? `Beech mein, ${hz}` : "Beech mein";
  return hz ? `${v} ${hz}` : v;
}
