// Udhaar ledger. Android: encrypted Room (SQLCipher). Web demo: in memory, optionally persisted to localStorage.
// Copy of assets/demo/seeded_ledger.json (kept inside web-demo so Vercel builds it).
import seed from "./seeded_ledger.json";

export interface Entry { id: number; name: string; amount: number; note: string; at: number; deleted?: boolean }
export interface Balance { name: string; balance: number }

export interface KV { getItem(k: string): string | null; setItem(k: string, v: string): void }

export class Ledger {
  entries: Entry[] = [];
  private nextId = 1;
  constructor(private store?: KV) {
    const saved = safe(() => store?.getItem("batao_ledger"));
    if (saved) { this.entries = JSON.parse(saved); this.nextId = Math.max(0, ...this.entries.map((e) => e.id)) + 1; }
    else this.seed();
  }
  private seed() {
    const day = 86_400_000, now = Date.now();
    for (const c of seed as { name: string; entries: { amount: number; note?: string; days_ago?: number }[] }[])
      for (const e of c.entries) this.entries.push({ id: this.nextId++, name: c.name, amount: e.amount, note: e.note ?? "", at: now - (e.days_ago ?? 0) * day });
  }
  private save() { safe(() => this.store?.setItem("batao_ledger", JSON.stringify(this.entries))); }
  names(): string[] { return [...new Set(this.entries.map((e) => e.name))]; }
  add(name: string, amount: number, note = ""): Entry {
    const e = { id: this.nextId++, name, amount, note, at: Date.now() };
    this.entries.push(e); this.save(); return e;
  }
  undo(id: number) { const e = this.entries.find((x) => x.id === id); if (e) { e.deleted = true; this.save(); } }
  balanceOf(name: string): number {
    return this.entries.filter((e) => !e.deleted && e.name.toLowerCase() === name.toLowerCase()).reduce((s, e) => s + e.amount, 0);
  }
  balances(): Balance[] {
    return this.names().map((name) => ({ name, balance: this.balanceOf(name) })).sort((a, b) => b.balance - a.balance || a.name.localeCompare(b.name));
  }
  top(n: number) { return this.balances().filter((b) => b.balance > 0).slice(0, n); }
}

function safe<T>(f: () => T): T | undefined { try { return f(); } catch { return undefined; } }

export type NameResult = { kind: "exact"; name: string } | { kind: "ambiguous"; options: string[] } | { kind: "new"; name: string };

/** "Ramesh" -> "Ramesh Kumar", or ask "Ramesh Kumar ya Ramesh Yadav?". Port of NameMatch.kt. */
export function resolveName(spoken: string, existing: string[]): NameResult {
  const s = spoken.trim().toLowerCase();
  const exact = existing.find((e) => e.toLowerCase() === s);
  if (exact) return { kind: "exact", name: exact };
  const first = s.split(" ")[0];
  const hits = existing.filter((e) => e.toLowerCase().split(" ")[0] === first && (s.split(" ").length === 1 || e.toLowerCase().startsWith(s)));
  if (hits.length === 0) return { kind: "new", name: spoken.trim().split(/\s+/).map((w) => w.charAt(0).toUpperCase() + w.slice(1)).join(" ") };
  if (hits.length === 1) return { kind: "exact", name: hits[0] };
  return { kind: "ambiguous", options: hits.slice(0, 3) };
}

export function pickName(tokens: string[], options: string[]): string | null {
  const toks = new Set(tokens.map((t) => t.toLowerCase()));
  const scored = options.map((o) => [o, o.toLowerCase().split(" ").slice(1).filter((w) => toks.has(w)).length] as const);
  const best = Math.max(...scored.map((s) => s[1]));
  if (best === 0) {
    if (["pehla", "pehle", "first"].some((w) => toks.has(w))) return options[0];
    if (["doosra", "dusra", "second"].some((w) => toks.has(w))) return options[1] ?? null;
    return null;
  }
  const winners = scored.filter((s) => s[1] === best);
  return winners.length === 1 ? winners[0][0] : null;
}

export function upiUri(upiId: string, payee: string, amount: number, note: string) {
  const q = new URLSearchParams({ pa: upiId, pn: payee, am: String(amount), cu: "INR", tn: note });
  return `upi://pay?${q.toString()}`;
}
