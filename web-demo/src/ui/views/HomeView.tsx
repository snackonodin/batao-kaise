import type { Assistant } from "../../engine";

const EXAMPLES = ["Bijli ka bill bharna hai", "Ramesh ko do sau udhaar", "Kiska kitna baaki hai?", "Ramesh se paisa kaise loon?", "Main atak gaya"];

export function HomeView({ a }: { a: Assistant }) {
  return (
    <div className="home">
      <div className="home-brand">
        <span className="logo" aria-hidden="true">ब</span>
        <div>
          <h1>Batao Kaise</h1>
          <p>Sunita Kirana Store</p>
        </div>
      </div>
      <p className="home-lead">Neeche mic dabaiye aur apni bhasha mein boliye.</p>
      <h2 className="home-sub">Aise bol sakte hain</h2>
      <ul className="examples">
        {EXAMPLES.map((t) => (
          <li key={t}><button onClick={() => a.heard(t)}>“{t}”</button></li>
        ))}
      </ul>
    </div>
  );
}
