import { FIELD_COLOR, FIELD_LABEL, type Assistant, type View } from "../../engine";

type V = Extract<View, { kind: "bill" }>;

export function BillView({ a, v }: { a: Assistant; v: V }) {
  return (
    <div className="bill">
      <div className="bill-img" style={{ aspectRatio: `${v.width} / ${v.height}` }}>
        <img src={v.image} alt="Sample electricity bill (fake, for the demo)" />
        {v.loading && <div className="bill-scan" aria-label="Bill padh raha hoon" />}
        {!v.loading && v.fields.map((f) => f.box && (
          <button
            key={f.kind}
            className={`bill-box${f.value ? "" : " unclear"}`}
            onClick={() => a.billTap(f.kind)}
            style={{
              left: `${(f.box[0] - 10) / v.width * 100}%`, top: `${(f.box[1] - 8) / v.height * 100}%`,
              width: `${(f.box[2] - f.box[0] + 20) / v.width * 100}%`, height: `${(f.box[3] - f.box[1] + 16) / v.height * 100}%`,
              ["--c" as string]: f.value ? FIELD_COLOR[f.kind] : "#8A8A8A",
            }}
            aria-label={`${FIELD_LABEL[f.kind]}: ${f.value ?? "saaf nahi"}`}
          >
            <span className="bill-box-label">{f.value ? FIELD_LABEL[f.kind] : "Saaf nahi"}</span>
          </button>
        ))}
      </div>
      {!v.loading && (
        <ul className="bill-legend">
          {v.fields.map((f) => (
            <li key={f.kind}>
              <button onClick={() => a.billTap(f.kind)} style={{ ["--c" as string]: f.value ? FIELD_COLOR[f.kind] : "#8A8A8A" }}>
                <span className="swatch" />{FIELD_LABEL[f.kind]}<b>{f.value ? fmt(f.kind, f.value) : "Saaf nahi"}</b>
              </button>
            </li>
          ))}
        </ul>
      )}
      <p className="note">{v.ocrNote}</p>
    </div>
  );
}

function fmt(kind: string, value: string) {
  if (kind === "account") return value.replace(/(\d{4})(?=\d)/g, "$1 ");
  if (kind === "amount") return `₹${value}`;
  return value;
}
