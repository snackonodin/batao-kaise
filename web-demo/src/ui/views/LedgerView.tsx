import type { Assistant, View } from "../../engine";

type V = Extract<View, { kind: "ledger" }>;

export function LedgerView({ a, v }: { a: Assistant; v: V }) {
  const total = v.balances.reduce((s, b) => s + Math.max(0, b.balance), 0);
  return (
    <div className="ledger">
      <div className="ledger-head">
        <h2>Udhaar khata</h2>
        <span>Kul baaki <b>₹{total.toLocaleString("en-IN")}</b></span>
      </div>
      <ul className="ledger-list">
        {v.balances.map((b) => (
          <li key={b.name} className={b.name === v.highlight ? "is-new" : ""}>
            <span className="avatar" aria-hidden="true">{b.name[0]}</span>
            <span className="ledger-name">{b.name}</span>
            <b className={b.balance > 0 ? "due" : "clear"}>{b.balance > 0 ? `₹${b.balance.toLocaleString("en-IN")}` : "Hisaab saaf"}</b>
          </li>
        ))}
      </ul>
      {v.lastEntryId != null && <button className="btn btn-ghost undo" onClick={() => a.undoLast()}>Galat likha? Hatao</button>}
      <p className="note">Web demo: khata isi browser tab mein hai. Android app mein ye phone par encrypted rehta hai.</p>
    </div>
  );
}
