import type { Assistant, State } from "../engine";
import { MIC_SUPPORTED, TTS_ON } from "./voice";

const TRY: { text: string; en: string }[] = [
  { text: "Bijli ka bill bharna hai", en: "Read a bill, then guided payment" },
  { text: "Ramesh ko do sau udhaar", en: "Write credit; similar names get checked" },
  { text: "Ramesh ne 100 de diye", en: "Record a repayment" },
  { text: "Kiska kitna baaki hai?", en: "Who owes the most" },
  { text: "Ramesh se paisa kaise loon?", en: "Reminder with UPI QR" },
  { text: "Main atak gaya", en: "Ask a trusted person for help" },
  { text: "umm", en: "Noise: it asks again, then shows pictures" },
];

const PHASE: Record<State["phase"], string> = {
  idle: "Ready", listening: "Listening", processing: "Understanding", speaking: "Speaking", guiding: "Guiding",
};

export function SidePanel({ a, s, onReset }: { a: Assistant; s: State; onReset(): void }) {
  return (
    <aside className="side" aria-label="For judges: what is happening">
      <p className="status"><span className={`dot dot-${s.phase}`} aria-hidden="true" />{PHASE[s.phase]}</p>
      <p className="side-caption" aria-live="polite">{s.caption}</p>
      {s.lastIntent && <p className="side-intent">Understood as <code>{s.lastIntent}</code></p>}

      <h2>Try saying</h2>
      <p className="side-hint">Speak into the mic, or click a line to send it as if spoken.</p>
      <ul className="try">
        {TRY.map((t) => (
          <li key={t.text}><button onClick={() => a.heard(t.text)}><span lang="hi">“{t.text}”</span><small>{t.en}</small></button></li>
        ))}
      </ul>
      <button className="btn btn-ghost side-reset" onClick={onReset}>Start over</button>

      <h2>What is real here</h2>
      <ul className="facts">
        <li>The voice engine (Hinglish numbers, intents, read-back, guide) is the same logic as the Android app, ported to TypeScript.</li>
        <li>The bill’s OCR is precomputed for this sample bill. Android runs ML Kit on the phone.</li>
        <li>Demo Pay, WhatsApp and UPI are mocks. No money or message leaves this page.</li>
        <li>Voice uses your browser{TTS_ON ? "" : " (speech is off: ?tts=0)"}; {MIC_SUPPORTED ? "Chrome works best for the mic." : "this browser has no speech recognition, so type instead."}</li>
      </ul>
    </aside>
  );
}
