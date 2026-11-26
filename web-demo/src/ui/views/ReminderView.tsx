import { useEffect, useState } from "react";
import QRCode from "qrcode";
import type { Assistant, View } from "../../engine";
import { Chat, ShareSheet } from "./Share";

type V = Extract<View, { kind: "reminder" }>;

export function ReminderView({ a, v }: { a: Assistant; v: V }) {
  const [qr, setQr] = useState("");
  useEffect(() => {
    let live = true;
    QRCode.toDataURL(v.upi, { margin: 1, width: 360, color: { dark: "#1B1B1B", light: "#FFFFFF" } }).then((u) => { if (live) setQr(u); });
    return () => { live = false; };
  }, [v.upi]);
  const qrImg = qr ? <img className="qr" src={qr} alt={`UPI QR for ₹${v.amount}`} /> : <span className="qr qr-empty" />;

  if (v.stage !== "draft") {
    return <Chat to={v.name} text={v.text} sent={v.stage === "sent"} highlight={v.highlight} onSend={() => a.share("send")}>{qrImg}</Chat>;
  }
  return (
    <div className="reminder">
      <div className="card">
        <h2 className="card-title">Yaad dilane ka sandesh</h2>
        <pre className="card-text">{v.text}</pre>
        <div className="qr-wrap">{qrImg}<span>UPI QR · ₹{v.amount.toLocaleString("en-IN")}<small>demo UPI id</small></span></div>
      </div>
      <ShareSheet highlight={v.highlight} onPick={(t) => a.share(t)} />
    </div>
  );
}
