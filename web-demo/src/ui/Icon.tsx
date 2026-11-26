// One hand-drawn icon set: 24px grid, 1.8 stroke, round caps. No emoji, no icon fonts.
const P: Record<string, string> = {
  bill: "M6 3h12v18l-2-1.4-2 1.4-2-1.4-2 1.4-2-1.4L6 21zM9 8h6M9 11.5h6M9 15h3.5",
  book: "M5 4.5A1.5 1.5 0 0 1 6.5 3H19v15H6.5A1.5 1.5 0 0 0 5 19.5zM5 19.5A1.5 1.5 0 0 0 6.5 21H19M9 7.5h6M9 11h4",
  help: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM9.6 9.4a2.5 2.5 0 1 1 3.4 2.3c-.6.3-1 .8-1 1.5v.6M12 17h.01",
  send: "M4 12h13M12 6l6 6-6 6",
  scan: "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3M8 9h3v3H8zM13 13h3v3h-3zM13 9h3M8 15h3",
  phone: "M8 3h8a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1zM11 18h2",
  bank: "M3 9.5 12 4l9 5.5M4.5 9.5h15M6 10v7M10 10v7M14 10v7M18 10v7M3.5 20h17",
  clock: "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18zM12 7v5l3.2 2",
  bolt: "M13 3 5 13.5h6L10 21l8-10.5h-6z",
  drop: "M12 3.5s6 6.4 6 10.5a6 6 0 0 1-12 0c0-4.1 6-10.5 6-10.5z",
  flame: "M12 21c3.6 0 6-2.4 6-5.6 0-3.6-3-5.5-3.6-9.4-1.8 1.2-2.9 3.2-2.9 5.1-1-.6-1.6-1.7-1.8-2.9C8 9.7 6 12.3 6 15.4 6 18.6 8.4 21 12 21z",
  tv: "M3.5 7h17v11h-17zM8 3.5 12 7l4-3.5M8 21h8",
  chevron: "M9.5 6l6 6-6 6",
  check: "M5 12.5l4.5 4.5L19 7.5",
  mail: "M3.5 6h17v12h-17zM3.5 6.5 12 13l8.5-6.5",
  ask: "M4 5h16v11H9l-4.5 3.5V16H4zM9 9h6M9 12h4",
  mic: "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3zM5.5 11a6.5 6.5 0 0 0 13 0M12 17.5V21M8.5 21h7",
  keys: "M3 6.5h18v11H3zM6.5 10h.01M9.5 10h.01M12.5 10h.01M15.5 10h.01M8 14h8",
  arrowSend: "M3.5 20 21 12 3.5 4l2.6 8zM6.1 12H13",
};

export function Icon({ name, size = 24, stroke = 1.8, label }: { name: keyof typeof P | string; size?: number; stroke?: number; label?: string }) {
  return (
    <svg viewBox="0 0 24 24" width={size} height={size} role={label ? "img" : undefined} aria-label={label} aria-hidden={label ? undefined : true}
      fill="none" stroke="currentColor" strokeWidth={stroke} strokeLinecap="round" strokeLinejoin="round">
      <path d={P[name]} />
    </svg>
  );
}
