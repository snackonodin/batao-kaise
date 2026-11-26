import { useEffect, useState } from "react";
import type { Assistant, View } from "../../engine";
import { Chat, ShareSheet } from "./Share";

type V = Extract<View, { kind: "rescue" }>;

export function RescueView({ a, v }: { a: Assistant; v: V }) {
  const [draft, setDraft] = useState(v.summary);
  const [copied, setCopied] = useState(false);
  useEffect(() => setDraft(v.summary), [v.summary]);
  const removed = Object.entries(v.removed).filter(([, n]) => n > 0);

  if (v.stage === "chat" || v.stage === "sent") {
    return <Chat to={v.to} text={v.summary} sent={v.stage === "sent"} highlight={v.highlight} onSend={() => a.share("send")} />;
  }
  if (v.stage === "ai") {
    const copy = async () => { try { await navigator.clipboard.writeText(v.aiQuestion); setCopied(true); } catch { setCopied(false); } };
    return (
      <div className="rescue">
        <div className="card">
          <p className="card-kicker">AI se poochne ke liye sawaal</p>
          <pre className="card-text">{v.aiQuestion}</pre>
          <button className="btn btn-ghost" onClick={copy}>{copied ? "Copy ho gaya" : "Copy karein"}</button>
        </div>
        <p className="note">Ise Gemini ya Claude mein chipka dijiye. Number pehle hi hata diye gaye hain.</p>
      </div>
    );
  }
  return (
    <div className="rescue">
      <div className="card">
        <p className="card-kicker">{v.to} ko jaane wala sandesh</p>
        <textarea className="card-edit" value={draft} rows={8} aria-label="Sandesh badliye"
          onChange={(e) => setDraft(e.target.value)} onBlur={() => draft !== v.summary && a.editRescue(draft)} />
        {removed.length > 0 && (
          <ul className="removed">
            {removed.map(([k, n]) => <li key={k}>{k} hataya{n > 1 ? ` ×${n}` : ""}</li>)}
          </ul>
        )}
        <p className="card-foot">OTP, PIN, account, Aadhaar, phone aur UPI number bhejne se pehle hata diye jaate hain.</p>
      </div>
      {v.stage === "share" && (
        <ShareSheet highlight={v.highlight} onPick={(t) => a.share(t)}
          extra={<button className="sheet-app" onClick={() => a.share("ai")}><span className="app-ic ai" aria-hidden="true">?</span>AI se poochein</button>} />
      )}
    </div>
  );
}
