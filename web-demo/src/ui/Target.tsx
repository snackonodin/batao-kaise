import { useEffect, useRef, type ReactNode } from "react";

type Arrow = "down" | "up" | "side";

/** Wraps a tappable element. When the engine highlights this id, it gets the pulsing amber outline and an arrow,
 *  and it scrolls into the visible part of the screen (never behind the assistant dock). */
export function Target({ id, highlight, children, block = false, arrow = "down" }: {
  id: string; highlight: string | null; children: ReactNode; block?: boolean; arrow?: Arrow;
}) {
  const on = highlight === id;
  const ref = useRef<HTMLSpanElement>(null);
  useEffect(() => {
    const el = ref.current, box = el?.closest(".screen");
    if (!on || !el || !box) return;
    // Scroll only the phone's screen, never the page around it.
    const reduce = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    const r = el.getBoundingClientRect(), b = box.getBoundingClientRect();
    const top = box.scrollTop + (r.top - b.top) - (b.height - r.height) / 2;
    if (r.top < b.top + 48 || r.bottom > b.bottom - 16) box.scrollTo({ top: Math.max(0, top), behavior: reduce ? "auto" : "smooth" });
  }, [on]);
  return (
    <span ref={ref} className={`target${block ? " target-block" : ""}${on ? ` hl hl-${arrow}` : ""}`} data-target={id}>
      {children}
      {on && <span className="hl-arrow" aria-hidden="true" />}
    </span>
  );
}
