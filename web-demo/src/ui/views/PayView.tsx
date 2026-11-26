// Mock "Demo Pay": a dummy payments app the guide teaches on. It reacts to taps like the real dummy app;
// screens change only when the user taps (a.payTap), never by themselves.
import { useEffect, useState } from "react";
import type { Assistant, View } from "../../engine";
import { Target } from "../Target";
import { Icon } from "../Icon";

type V = Extract<View, { kind: "pay" }>;
const rupees = (n: number) => `₹${n.toLocaleString("en-IN")}`;

export function PayView({ a, v }: { a: Assistant; v: V }) {
  const tap = (id: string) => a.payTap(id);
  const hl = v.sensitive ? null : v.highlight;

  return (
    <div className={`pay pay-${v.screen}`}>
      <div className="pay-bar">
        {v.screen !== "home" && v.screen !== "success"
          ? <Target highlight={hl} id="back_btn" arrow="up"><button className="pay-back" onClick={() => tap("back_btn")} aria-label="Back">Back</button></Target>
          : <span className="pay-logo">Demo<b>Pay</b></span>}
        <span className="pay-title">{TITLE[v.screen]}</span>
      </div>
      <div className="pay-demo" role="note">DEMO APP · no real money</div>

      <div className="pay-body">
        {v.screen === "home" && <>
          <div className="pay-hello">
            <span>Namaste, Sunita</span>
            <b>Demo balance {rupees(5000)}</b>
          </div>
          <div className="tiles">
            <Tile id="send_tile" icon="send" label="Paise bhejein" onTap={tap} hl={hl} />
            <Tile id="scan_tile" icon="scan" label="QR scan" onTap={tap} hl={hl} />
            <Tile id="recharge_tile" icon="phone" label="Recharge" onTap={tap} hl={hl} />
            <Tile id="bills_tile" icon="bill" label="Bills" onTap={tap} hl={hl} />
            <Tile id="balance_tile" icon="bank" label="Balance" onTap={tap} hl={hl} />
            <Tile id="history_tile" icon="clock" label="History" onTap={tap} hl={hl} />
          </div>
        </>}

        {v.screen === "bills" && (
          <ul className="list">
            <ListItem id="electricity_tile" icon="bolt" label="Electricity / Bijli" onTap={tap} hl={hl} />
            <ListItem id="water_tile" icon="drop" label="Water / Paani" onTap={tap} hl={hl} />
            <ListItem id="gas_tile" icon="flame" label="Gas cylinder" onTap={tap} hl={hl} />
            <ListItem id="dth_tile" icon="tv" label="DTH / TV" onTap={tap} hl={hl} />
            <ListItem id="mobile_tile" icon="phone" label="Mobile postpaid" onTap={tap} hl={hl} />
          </ul>
        )}

        {v.screen === "electricity" && <>
          <div className="field-ro"><span>Board</span><b>Demo Vidyut Vitran Nigam</b></div>
          <label className="field" htmlFor="account_field">
            <span>Consumer / Account number</span>
            <Target highlight={hl} id="account_field" block arrow="side">
              <input id="account_field" inputMode="numeric" autoComplete="off" placeholder="12 ank ka number"
                value={v.accountTyped.replace(/(\d{4})(?=\d)/g, "$1 ")}
                onFocus={() => tap("account_field")}
                onChange={(e) => a.payType("account_field", e.target.value)} />
            </Target>
          </label>
          {v.accountOk
            ? <div className="consumer"><span>Sunita Devi</span><b>Bill {rupees(v.amount)}</b><small>Due 15/12/2026</small></div>
            : v.accountTyped.length === 12 && <p className="field-err">Ye number nahi mila. Bill wala number daaliye.</p>}
          <Target highlight={hl} id="continue_btn" block arrow="side">
            <button className="pay-primary" disabled={!v.accountOk} onClick={() => tap("continue_btn")}>Continue</button>
          </Target>
        </>}

        {v.screen === "pay" && <>
          <div className="pay-summary">
            <span>Demo Vidyut Vitran Nigam</span>
            <small>Sunita Devi · {v.account.replace(/(\d{4})(?=\d)/g, "$1 ")}</small>
            <b>{rupees(v.amount)}</b>
          </div>
          <Target highlight={hl} id="pay_btn" block arrow="side">
            <button className="pay-primary" onClick={() => tap("pay_btn")}>Pay {rupees(v.amount)}</button>
          </Target>
        </>}

        {v.screen === "pin" && <PinScreen a={a} v={v} />}

        {v.screen === "success" && (
          <div className="success">
            <span className="tick" aria-hidden="true"><Icon name="check" size={44} stroke={2.6} /></span>
            <b>{rupees(v.amount)} paid</b>
            <span>Demo Vidyut Vitran Nigam</span>
            <small className="success-note">Demo payment. Koi paisa nahi gaya.</small>
            <button className="pay-secondary" onClick={() => tap("home_btn")}>Home</button>
          </div>
        )}

        {v.screen === "other" && (
          <div className="wrong">
            <label className="field"><span>Mobile number ya UPI ID</span><input disabled placeholder="98xxxxxx10" /></label>
            <button className="pay-primary" disabled>Aage badhein</button>
          </div>
        )}
      </div>
    </div>
  );
}

const TITLE: Record<V["screen"], string> = {
  home: "", bills: "Bills", electricity: "Electricity", pay: "Pay", pin: "UPI PIN", success: "", other: "Paise bhejein",
};

function Tile({ id, icon, label, onTap, hl }: { id: string; icon: string; label: string; onTap(id: string): void; hl: string | null }) {
  return <Target highlight={hl} id={id} block><button className="tile" onClick={() => onTap(id)}><Icon name={icon} size={26} />{label}</button></Target>;
}
function ListItem({ id, icon, label, onTap, hl }: { id: string; icon: string; label: string; onTap(id: string): void; hl: string | null }) {
  return <li><Target highlight={hl} id={id} block arrow="side"><button className="list-item" onClick={() => onTap(id)}><Icon name={icon} size={22} /><span className="li-label">{label}</span><Icon name="chevron" size={20} /></button></Target></li>;
}

/** The guide hides here: no highlight, no arrow. Only the PIN's length reaches the engine. */
function PinScreen({ a, v }: { a: Assistant; v: V }) {
  const [pin, setPin] = useState("");
  useEffect(() => () => setPin(""), []);
  return (
    <div className="pin">
      <span className="pin-to">{`Demo Vidyut Vitran Nigam · ₹${v.amount.toLocaleString("en-IN")}`}</span>
      <label htmlFor="pin_field">4 ank ka UPI PIN daaliye</label>
      <input id="pin_field" type="password" inputMode="numeric" autoComplete="off" maxLength={4} value={pin}
        onFocus={() => a.payTap("pin_field")}
        onChange={(e) => { const d = e.target.value.replace(/\D/g, "").slice(0, 4); setPin(d); a.payType("pin_field", d); }} />
      <div className="pin-dots" aria-hidden="true">{[0, 1, 2, 3].map((i) => <i key={i} className={i < pin.length ? "on" : ""} />)}</div>
      <p className="pin-note">Guide yahan chhup jaata hai. PIN sirf aap daaliye. (Demo: koi bhi 4 ank)</p>
      <button className="pay-primary" disabled={pin.length !== 4} onClick={() => a.payTap("pin_submit")}>Submit</button>
    </div>
  );
}
