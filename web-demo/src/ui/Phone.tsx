import { useState } from "react";
import type { Assistant, State } from "../engine";
import { MIC_SUPPORTED } from "./voice";
import { Icon } from "./Icon";
import { HomeView } from "./views/HomeView";
import { BillView } from "./views/BillView";
import { PayView } from "./views/PayView";
import { LedgerView } from "./views/LedgerView";
import { ReminderView } from "./views/ReminderView";
import { RescueView } from "./views/RescueView";

interface Props { a: Assistant; s: State; micError: string; clearMicError(): void }

export function Phone({ a, s, micError, clearMicError }: Props) {
  const v = s.view;
  return (
    <div className="phone" aria-label="Phone screen (simulated)">
      <div className="statusbar" aria-hidden="true">
        <span>9:41</span>
        <span className="sb-icons"><i className="sb-signal" /><i className="sb-batt" /></span>
      </div>
      <div className={`screen view-${v.kind}`}>
        {v.kind === "home" && <HomeView a={a} />}
        {v.kind === "bill" && <BillView a={a} v={v} />}
        {v.kind === "pay" && <PayView a={a} v={v} />}
        {v.kind === "ledger" && <LedgerView a={a} v={v} />}
        {v.kind === "reminder" && <ReminderView a={a} v={v} />}
        {v.kind === "rescue" && <RescueView a={a} v={v} />}
      </div>
      <Dock a={a} s={s} micError={micError} clearMicError={clearMicError} />
      <div className="navbar" aria-hidden="true"><i /></div>
    </div>
  );
}

function Dock({ a, s, micError, clearMicError }: Props) {
  const [typed, setTyped] = useState("");
  const [showType, setShowType] = useState(!MIC_SUPPORTED);
  const listening = s.phase === "listening";
  const hideGuide = s.view.kind === "pay" && s.view.sensitive;

  const send = () => {
    const t = typed.trim(); if (!t) return;
    setTyped(""); clearMicError(); a.heard(t);
  };
  const mic = () => { clearMicError(); a.micTapped(); };

  return (
    <section className={`dock${hideGuide ? " dock-quiet" : ""}`} aria-live="polite">
      <p className={`bubble${s.phase === "speaking" ? " is-speaking" : ""}`}>{s.bubble}</p>
      <p className="live" aria-label="Aapne kaha">
        {listening && !s.live ? <span className="live-hint">Sun raha hoon…</span> : s.live ? <>“{s.live}”</> : " "}
      </p>
      {micError && <p className="mic-error">{micError}</p>}

      {(s.showYesNo || s.showPictures || s.showDone) && (
        <div className="choices">
          {s.showYesNo && <>
            <button className="btn btn-yes" onClick={() => a.yes()}>Haan</button>
            <button className="btn btn-no" onClick={() => a.no()}>Nahi</button>
          </>}
          {s.showPictures && <>
            <button className="pic" onClick={() => a.picture("bill")}><Icon name="bill" size={30} />Bill</button>
            <button className="pic" onClick={() => a.picture("udhaar")}><Icon name="book" size={30} />Udhaar</button>
            <button className="pic" onClick={() => a.picture("madad")}><Icon name="help" size={30} />Phone madad</button>
          </>}
          {s.showDone && !s.showYesNo && <button className="btn btn-done" onClick={() => a.done()}>Ho gaya</button>}
        </div>
      )}

      <div className="mic-row">
        <button className={`type-toggle${showType ? " on" : ""}`} onClick={() => setShowType(!showType)} aria-label="Likhkar boliye" aria-pressed={showType}>
          <Icon name="keys" size={24} />
        </button>
        <button className={`mic${listening ? " is-listening" : ""}`} onClick={mic} aria-label={listening ? "Sunna band karein" : "Mic dabaiye aur boliye"}>
          <span className="mic-ring" aria-hidden="true" />
          <Icon name="mic" size={34} stroke={2.2} />
        </button>
        <span className="mic-spacer" />
      </div>

      {showType && (
        <form className="type-box" onSubmit={(e) => { e.preventDefault(); send(); }}>
          <input value={typed} onChange={(e) => setTyped(e.target.value)} placeholder="Ya yahan likhiye…" aria-label="Likhkar boliye" lang="hi" />
          <button type="submit" disabled={!typed.trim()}>Bhejo</button>
        </form>
      )}
    </section>
  );
}
