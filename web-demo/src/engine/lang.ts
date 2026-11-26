// Language layer: Devanagari -> Hinglish, synonyms, spoken numbers.
// Port of app/src/main/java/com/bataokaise/app/voice/*.kt (same behaviour, same tests).

const VOWELS: Record<string, string> = {
  "अ": "a", "आ": "aa", "इ": "i", "ई": "ee", "उ": "u", "ऊ": "oo", "ऋ": "ri", "ए": "e", "ऐ": "ai", "ओ": "o", "औ": "au", "ऑ": "o", "ऍ": "e",
};
const MATRAS: Record<string, string> = {
  "ा": "aa", "ि": "i", "ी": "ee", "ु": "u", "ू": "oo", "ृ": "ri", "े": "e", "ै": "ai", "ो": "o", "ौ": "au", "ॉ": "o", "ॅ": "e",
};
const CONS: Record<string, string> = {
  "क": "k", "ख": "kh", "ग": "g", "घ": "gh", "ङ": "n", "च": "ch", "छ": "chh", "ज": "j", "झ": "jh", "ञ": "n",
  "ट": "t", "ठ": "th", "ड": "d", "ढ": "dh", "ण": "n", "त": "t", "थ": "th", "द": "d", "ध": "dh", "न": "n",
  "प": "p", "फ": "ph", "ब": "b", "भ": "bh", "म": "m", "य": "y", "र": "r", "ल": "l", "व": "v", "श": "sh", "ष": "sh",
  "स": "s", "ह": "h", "ळ": "l",
};
const NUKTA: Record<string, string> = { "क": "q", "ख": "kh", "ग": "g", "ज": "z", "ड": "d", "ढ": "dh", "फ": "f" };
const HALANT = "्", NUKTA_SIGN = "़";

export const hasDevanagari = (s: string) => /[ऀ-ॿ]/.test(s);

type Unit = { cons: string | null; vowel: string; schwa: boolean };

function translitWord(word: string): string {
  const units: Unit[] = [];
  let out = "";
  const flush = () => {
    if (!units.length) return;
    const last = units[units.length - 1];
    if (last.schwa && units.length > 1) last.vowel = "";
    if (last.vowel === "ee" && units.length > 1) last.vowel = "i";
    for (let k = units.length - 2; k >= 1; k--) {
      const u = units[k];
      if (u.schwa && u.cons && units[k - 1].vowel && units[k + 1].cons && units[k + 1].vowel) u.vowel = "";
    }
    out += units.map((u) => (u.cons ?? "") + u.vowel).join("");
    units.length = 0;
  };
  const chars = [...word];
  let i = 0;
  while (i < chars.length) {
    const c = chars[i];
    if (CONS[c]) {
      let cons = CONS[c];
      let j = i + 1;
      if (chars[j] === NUKTA_SIGN) { cons = NUKTA[c] ?? cons; j++; }
      const after = chars[j];
      if (after === HALANT) { units.push({ cons, vowel: "", schwa: false }); j++; }
      else if (after && MATRAS[after]) { units.push({ cons, vowel: MATRAS[after], schwa: false }); j++; }
      else units.push({ cons, vowel: "a", schwa: true });
      i = j;
    } else if (VOWELS[c]) { units.push({ cons: null, vowel: VOWELS[c], schwa: false }); i++; }
    else if (c === "ं" || c === "ँ") { if (units.length) units[units.length - 1].vowel += "n"; else out += "n"; i++; }
    else if (c === "ः") { if (units.length) units[units.length - 1].vowel += "h"; i++; }
    else if (c >= "०" && c <= "९") { flush(); out += String(c.charCodeAt(0) - "०".charCodeAt(0)); i++; }
    else if (c === "।") { flush(); out += "."; i++; }
    else { flush(); out += c; i++; }
  }
  flush();
  return out;
}

export function toLatin(s: string): string {
  if (!hasDevanagari(s)) return s;
  return s.split(/(\s+)/).map((w) => (hasDevanagari(w) ? translitWord(w) : w)).join("");
}

/** Collapse spelling variants: "udhaar" == "udhar", "bijlee" == "bijli". */
export const squash = (w: string) =>
  w.toLowerCase().replace(/ee/g, "i").replace(/oo/g, "u").replace(/w/g, "v").replace(/(.)\1+/g, "$1");

export const SYNONYMS: Record<string, string[]> = {
  udhaar: ["udhaar", "udhar", "udhari", "udhaari", "baaki", "baki", "baakee", "karja", "karjaa", "karza", "karz", "qarz", "credit"],
  bijli: ["bijli", "bijuli", "bijlee", "bijali", "light", "lite", "electricity", "electric", "current", "bijlii"],
  paisa: ["paisa", "paise", "paisaa", "rupaye", "rupay", "rupaya", "rupee", "rupees", "rupiya", "rs", "rupaiya", "rupaiye", "rupye", "rupe", "rupayee"],
  bill: ["bill", "bil", "biil", "bila"],
  bharna: ["bharna", "bharni", "bhar", "bharu", "bharoon", "bharun", "bharen", "bharein", "pay", "payment", "jama", "chukana", "bhugtan", "bharana", "bhrna"],
  yojana: ["yojana", "yojna", "yojnaa", "scheme", "schemes", "sarkari", "sarkaari", "subsidy", "pension"],
  padho: ["padho", "padh", "padhna", "padhiye", "padhke", "read", "sunao", "bataao", "batao", "dekho"],
  diye: ["diye", "diya", "de", "di", "die", "diae", "diyae", "lauta", "lautaye", "lautaya", "wapas", "vapas", "chukaye", "chukaya", "diyea"],
  kitna: ["kitna", "kitne", "kitni", "kitnaa", "kitana", "kya"],
  kiska: ["kiska", "kiske", "kiski", "kis", "kaun", "kon", "kisne"],
  yaad: ["yaad", "yad", "reminder", "remind", "yaaddihani", "dilao", "dilaun", "dilana"],
  kaise: ["kaise", "kese", "kaisay", "how", "kaisa"],
  loon: ["loon", "lun", "lu", "loo", "lena", "lein", "len", "maangu", "mangu", "vasool", "wasool"],
  atak: ["atak", "atka", "atki", "atke", "fas", "phas", "phans", "fans", "phasa", "fasa", "stuck"],
  haan: ["haan", "ha", "han", "haa", "haanji", "hanji", "ji", "yes", "sahi", "theek", "thik", "ok", "okay", "bilkul", "hmm", "achha", "acha"],
  nahi: ["nahi", "nahin", "nai", "na", "no", "galat", "mat", "nako"],
  ruko: ["ruko", "ruk", "rukiye", "bas", "stop", "band"],
  phirse: ["phirse", "firse", "dobara", "repeat"],
  peeche: ["peeche", "piche", "pichhe", "peechhe", "back", "wapas"],
  naya: ["naya", "nayaa", "new"],
  kaam: ["kaam", "kam", "task"],
  ho: ["ho", "hoo"],
  gaya: ["gaya", "gya", "gayi", "gai", "gaye", "done"],
};

const LOOKUP: Map<string, string> = (() => {
  const m = new Map<string, string>();
  for (const [canon, vars] of Object.entries(SYNONYMS)) for (const v of vars) if (!m.has(squash(v))) m.set(squash(v), canon);
  return m;
})();

export const FILLERS = new Set(["umm", "um", "uh", "hmmm", "arre", "are", "arey", "yaar", "bhai", "bhaiya", "ji", "matlab", "woh", "wo", "na", "toh", "to", "please", "plz", "accha"]);

export function tokenize(s: string): string[] {
  return s.toLowerCase()
    .replace(/₹/g, " rs ")
    .replace(/(\d),(?=\d)/g, "$1")
    .replace(/(\d)([a-z])/g, "$1 $2")
    .split(/[^\p{L}\p{N}.]+/u)
    .map((t) => t.replace(/^\.+|\.+$/g, ""))
    .filter(Boolean);
}

export const canon = (tok: string) => LOOKUP.get(squash(tok)) ?? tok;

export interface Normalized {
  raw: string;
  latin: string;
  tokens: string[];   // Latin tokens as heard (names)
  canon: string[];    // canonical tokens (rules), same length
}

export function normalize(raw: string): Normalized {
  const latin = toLatin(raw);
  const toks = tokenize(latin);
  const joined: string[] = [];
  for (let i = 0; i < toks.length; i++) {
    const a = toks[i], b = toks[i + 1];
    if (b !== undefined && ["phir", "fir"].includes(squash(a)) && squash(b) === "se") { joined.push("phirse"); i++; continue; }
    joined.push(a);
  }
  return { raw, latin, tokens: joined, canon: joined.map(canon) };
}

export const wordCount = (n: Normalized) => n.tokens.filter((t) => !FILLERS.has(t)).length;
export const has = (n: Normalized, ...w: string[]) => w.every((x) => n.canon.includes(x));
export const hasAny = (n: Normalized, ...w: string[]) => w.some((x) => n.canon.includes(x));

// ---------- Numbers ----------

const HINDI_UNITS = [
  "ek", "do", "teen", "char", "paanch", "chhah", "saat", "aath", "nau", "das",
  "gyarah", "barah", "terah", "chaudah", "pandrah", "solah", "satrah", "atharah", "unnis", "bees",
  "ikkis", "baais", "teis", "chaubis", "pachchis", "chhabbis", "sattais", "atthais", "untis", "tees",
  "ikattis", "battis", "taintis", "chauntis", "paintis", "chhattis", "saintis", "adtis", "untalis", "chalis",
  "iktalis", "bayalis", "taintalis", "chavalis", "paintalis", "chhiyalis", "saintalis", "adtalis", "unchas", "pachas",
  "ikyavan", "bavan", "tirpan", "chauvan", "pachpan", "chhappan", "sattavan", "atthavan", "unsath", "saath",
  "iksath", "basath", "tirsath", "chausath", "painsath", "chhiyasath", "sadsath", "adsath", "unhattar", "sattar",
  "ikhattar", "bahattar", "tihattar", "chauhattar", "pachhattar", "chhihattar", "satattar", "athattar", "unasi", "assi",
  "ikyasi", "bayasi", "tirasi", "chaurasi", "pachasi", "chhiyasi", "sattasi", "athasi", "navasi", "nabbe",
  "ikyanve", "banve", "tiranve", "chauranve", "pachanve", "chhiyanve", "sattanve", "atthanve", "ninyanve",
];
const EN = ["one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven", "twelve", "thirteen",
  "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen"];
const EN_TENS = ["twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"];

function sqMap<V>(entries: [string, V][]): Map<string, V> {
  const m = new Map<string, V>();
  for (const [k, v] of entries) if (!m.has(squash(k))) m.set(squash(k), v);
  return m;
}
const UNITS = sqMap<number>([
  ...HINDI_UNITS.map((w, i) => [w, i + 1] as [string, number]),
  ...Object.entries({
    tin: 3, chaar: 4, panch: 5, chhe: 6, che: 6, chha: 6, sat: 7, ath: 8, nao: 9, dus: 10, gyaarah: 11, baarah: 12,
    pandra: 15, sola: 16, unees: 19, bis: 20, pachees: 25, pachis: 25, tis: 30, chaalis: 40, chalees: 40, pachaas: 50,
    sath: 60, assee: 80, nabbay: 90, nabe: 90,
  }),
  ...EN.map((w, i) => [w, i + 1] as [string, number]),
  ...EN_TENS.map((w, i) => [w, (i + 2) * 10] as [string, number]),
]);
const HUNDRED = new Set(["sau", "so", "sow", "hundred"].map(squash));
const THOUSAND = new Set(["hazaar", "hazar", "hajar", "hajaar", "thousand", "hazaro"].map(squash));
const LAKH = new Set(["lakh", "laakh", "lac", "lakhs"].map(squash));
const FRACTIONS = sqMap<number>(Object.entries({ dedh: 1.5, dhai: 2.5, dhaai: 2.5, adhai: 2.5, dhaee: 2.5, dhaaee: 2.5 }));
const MODIFIERS = sqMap<number>(Object.entries({ saade: 0.5, sade: 0.5, saadhe: 0.5, sava: 0.25, sawa: 0.25, paune: -0.25 }));

const numeric = (t: string) => (/^\d+(\.\d+)?$/.test(t.replace(/,/g, "")) ? parseFloat(t.replace(/,/g, "")) : null);

export function isNumberWord(raw: string): boolean {
  const t = squash(raw);
  return UNITS.has(t) || HUNDRED.has(t) || THOUSAND.has(t) || LAKH.has(t) || FRACTIONS.has(t) || MODIFIERS.has(t) ||
    numeric(raw) !== null || t === "and" || t === "aur";
}

export function findNumbers(tokens: string[]): number[] {
  const res: number[] = [];
  let total = 0, cur = 0, mod = 0, active = false;
  const close = () => { if (active) res.push(Math.round(total + cur)); total = 0; cur = 0; mod = 0; active = false; };
  const takeMod = () => { const m = mod; mod = 0; return m; };
  tokens.forEach((raw, idx) => {
    const n = numeric(raw);
    const t = squash(raw);
    const next = tokens[idx + 1];
    if ((t === "and" || t === "aur") && active && next && isNumberWord(next) && !["and", "aur"].includes(next)) return;
    if (MODIFIERS.has(t)) { if (active && cur !== 0) close(); mod = MODIFIERS.get(t)!; active = true; }
    else if (FRACTIONS.has(t)) { cur += FRACTIONS.get(t)!; active = true; }
    else if (n !== null) { cur += n + takeMod(); active = true; }
    else if (UNITS.has(t)) {
      if (cur !== 0 && cur % 100 !== 0 && cur < 100) close();
      cur += UNITS.get(t)! + takeMod(); active = true;
    }
    else if (HUNDRED.has(t)) { cur = (active && cur !== 0 ? cur : 1) * 100; active = true; }
    else if (THOUSAND.has(t)) { total += (cur === 0 ? 1 : cur) * 1000; cur = 0; active = true; }
    else if (LAKH.has(t)) { total += (cur === 0 ? 1 : cur) * 100000; cur = 0; active = true; }
    else close();
  });
  close();
  return res;
}

export const parseNumber = (text: string) => findNumbers(tokenize(toLatin(text).toLowerCase()))[0] ?? null;

/** "451277883301" -> "4-5-1-2-7-7-8-8-3-3-0-1" (shown); spoken as single digits. */
export const digits = (s: string) => [...s.replace(/\D/g, "")].join("-");
/** "451277883301" -> "4-5-1-2, 7-7-8-8, 3-3-0-1": digit by digit, in the groups printed on the bill, with pauses. */
export const digitGroups = (s: string) => (s.replace(/\D/g, "").match(/.{1,4}/g) ?? []).map((g) => [...g].join("-")).join(", ");
/** What the speech engine actually reads: digits separated so they are said one by one. */
export const spokenForm = (line: string) => line.replace(/(\d)-(?=\d)/g, "$1 ").replace(/₹/g, " rupaye ");
