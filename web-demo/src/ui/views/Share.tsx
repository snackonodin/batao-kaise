// Mock share sheet and mock chat, shared by the reminder and rescue views. Nothing leaves the browser.
import type { ReactNode } from "react";
import { Target } from "../Target";

export function ShareSheet({ highlight, onPick, extra }: { highlight: string | null; onPick(t: "whatsapp" | "cancel"): void; extra?: ReactNode }) {
  return (
    <div className="sheet" role="dialog" aria-label="Share">
      <span className="sheet-grip" aria-hidden="true" />
      <p className="sheet-title">Kisko bhejein? <small>(mock, kuch nahi jaata)</small></p>
      <div className="sheet-apps">
        <Target id="share_whatsapp" highlight={highlight}>
          <button className="sheet-app" onClick={() => onPick("whatsapp")}><span className="app-ic wa" aria-hidden="true">W</span>WhatsApp</button>
        </Target>
        <button className="sheet-app" disabled><span className="app-ic sms" aria-hidden="true">✉</span>SMS</button>
        {extra}
      </div>
      <button className="sheet-cancel" onClick={() => onPick("cancel")}>Nahi bhejna</button>
    </div>
  );
}

export function Chat({ to, text, sent, highlight, onSend, children }: {
  to: string; text: string; sent: boolean; highlight: string | null; onSend(): void; children?: ReactNode;
}) {
  return (
    <div className="chat">
      <div className="chat-bar"><span className="avatar" aria-hidden="true">{to[0]}</span><b>{to}</b><small>WhatsApp (mock)</small></div>
      <div className="chat-body">
        {sent && <div className="msg out"><pre>{text}</pre>{children}<span className="ticks" aria-label="sent">✓✓</span></div>}
      </div>
      {!sent && (
        <div className="chat-compose">
          <div className="compose-text"><pre>{text}</pre>{children}</div>
          <Target id="send" highlight={highlight}>
            <button className="send" onClick={onSend} aria-label="Send">
              <svg viewBox="0 0 24 24" width="24" height="24" aria-hidden="true"><path d="M3 20.5 21 12 3 3.5v6.6l12 1.9-12 1.9z" fill="currentColor"/></svg>
            </button>
          </Target>
        </div>
      )}
      {sent && <p className="chat-sent">Bhej diya (demo: asli mein kuch nahi gaya)</p>}
    </div>
  );
}
