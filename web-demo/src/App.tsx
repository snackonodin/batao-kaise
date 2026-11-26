import { useEffect, useMemo, useState } from "react";
import { createAssistant, type State } from "./engine";
import { connectVoice, hush } from "./ui/voice";
import { Phone } from "./ui/Phone";
import { SidePanel } from "./ui/SidePanel";

export default function App() {
  // No store: every page load starts from the same seeded ledger, so the demo is repeatable.
  const a = useMemo(() => createAssistant(), []);
  const [s, setS] = useState<State>(a.state);
  const [micError, setMicError] = useState("");

  useEffect(() => {
    const unsub = a.subscribe(setS);
    const off = connectVoice(a, { onMicError: setMicError });
    (window as any).__batao = a; // for scripted screen recording (scripts/video)
    return () => { unsub(); off(); };
  }, [a]);

  const reset = () => { hush(); setMicError(""); a.reset(); };

  return (
    <div className="page">
      <header className="banner" role="note">
        <strong>Web demo</strong>; the real app is Android. Fake data only, no real money.
      </header>
      <main className="stage">
        <Phone a={a} s={s} micError={micError} clearMicError={() => setMicError("")} />
        <SidePanel a={a} s={s} onReset={reset} />
      </main>
    </div>
  );
}
