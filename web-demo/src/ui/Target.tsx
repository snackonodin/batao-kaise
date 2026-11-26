import type { ReactNode } from "react";

/** Wraps a tappable element. When the engine highlights this id, it gets the pulsing outline and an arrow. */
export function Target({ id, highlight, children, block = false, arrow = "down" }: {
  id: string; highlight: string | null; children: ReactNode; block?: boolean; arrow?: "down" | "up";
}) {
  const on = highlight === id;
  return (
    <span className={`target${block ? " target-block" : ""}${on ? ` hl hl-${arrow}` : ""}`} data-target={id}>
      {children}
      {on && <span className="hl-arrow" aria-hidden="true" />}
    </span>
  );
}

/** Small "DEMO" tag every mock screen carries. */
export function DemoTag({ children = "DEMO, no real money" }: { children?: ReactNode }) {
  return <span className="demo-tag">{children}</span>;
}
