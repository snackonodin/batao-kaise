// Browser I/O for the engine: speak state.say, listen when state.listen, load the bill OCR when state.needBill.
// The engine never touches the speaker or mic; this file is the only place that does.
import type { Assistant, State } from "../engine";

const params = new URLSearchParams(location.search);
/** ?tts=0 turns speech off (lines still advance on a timer). Used for screen recording, where voices are dubbed later. */
export const TTS_ON = params.get("tts") !== "0" && typeof window !== "undefined" && "speechSynthesis" in window;

type Rec = {
  lang: string; interimResults: boolean; continuous: boolean; maxAlternatives: number;
  onresult: ((e: any) => void) | null; onerror: ((e: any) => void) | null; onend: (() => void) | null;
  start(): void; abort(): void;
};
const RecCtor: (new () => Rec) | undefined = (window as any).SpeechRecognition ?? (window as any).webkitSpeechRecognition;
/** ?rec=1: screen recording; the scripted "user" speaks through the engine, so the type box stays closed. */
export const RECORDING = params.get("rec") === "1";
export const MIC_SUPPORTED = !!RecCtor && params.get("mic") !== "0";

let hindiVoice: SpeechSynthesisVoice | null | undefined;
function pickVoice(): SpeechSynthesisVoice | null {
  if (hindiVoice !== undefined && hindiVoice !== null) return hindiVoice;
  const vs = speechSynthesis.getVoices();
  hindiVoice = vs.find((v) => v.lang === "hi-IN") ?? vs.find((v) => v.lang.startsWith("hi")) ?? vs.find((v) => v.lang === "en-IN") ?? null;
  return hindiVoice;
}

/** Rough speaking time, used when TTS is off or never reports the end (some browsers drop onend). */
const estimateMs = (text: string, slow: boolean) => Math.min(9000, 700 + text.length * (slow ? 75 : 58));

export interface VoiceHooks { onMicError(msg: string): void }

export function connectVoice(a: Assistant, hooks: VoiceHooks): () => void {
  let lastSay = 0;
  let wasListening = false;
  let billLoading = false;
  let rec: Rec | null = null;
  let fallback: number | undefined;

  const stopRec = () => { if (rec) { rec.onend = null; rec.onresult = null; rec.onerror = null; try { rec.abort(); } catch { /* already stopped */ } rec = null; } };

  const speak = (s: NonNullable<State["say"]>) => {
    clearTimeout(fallback);
    let timer: number | undefined, finished = false;
    const finish = () => { if (finished) return; finished = true; clearTimeout(timer); a.spoken(s.id); };
    if (!TTS_ON) {
      // Recording hooks (scripts/video): hold each line exactly as long as its dubbed audio, and log when it starts.
      const w = window as any;
      w.__onSay?.(s.text);
      const ms = w.__sayMs?.(s.text) ?? estimateMs(s.text, s.slow);
      fallback = timer = window.setTimeout(finish, ms);
      return;
    }
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(s.text);
    u.lang = "hi-IN"; u.rate = s.slow ? 0.7 : 0.9;
    const v = pickVoice(); if (v) u.voice = v;
    u.onend = finish;
    u.onerror = (e) => { if (e.error !== "interrupted" && e.error !== "canceled") finish(); };
    // Safety net: if the browser never fires onend, move on anyway.
    fallback = timer = window.setTimeout(finish, estimateMs(s.text, s.slow) * 2 + 2000);
    speechSynthesis.speak(u);
  };

  const listen = () => {
    if (TTS_ON) speechSynthesis.cancel();
    clearTimeout(fallback);
    stopRec();
    if (!MIC_SUPPORTED) return; // the "type instead" box stays available; state.listen stays true until heard()
    const r = new RecCtor!();
    r.lang = "hi-IN"; r.interimResults = true; r.continuous = false; r.maxAlternatives = 1;
    let gotFinal = false, gotAny = false, errored = false;
    r.onresult = (e: any) => {
      let text = "", final = false;
      for (let i = e.resultIndex; i < e.results.length; i++) { text += e.results[i][0].transcript; if (e.results[i].isFinal) final = true; }
      gotAny = true;
      if (final) { gotFinal = true; stopRec(); a.heard(text.trim()); } else a.partial(text);
    };
    r.onerror = (e: any) => {
      errored = true; stopRec();
      if (e.error === "no-speech") a.silence();
      else if (e.error === "no-match") a.noMatch();
      else if (e.error === "aborted") return;
      else { hooks.onMicError(e.error === "not-allowed" || e.error === "service-not-allowed" ? "Mic ki ijaazat nahi mili. Neeche likhkar boliye." : "Mic nahi chala. Neeche likhkar boliye."); a.micTapped(); }
    };
    r.onend = () => {
      rec = null;
      if (gotFinal || errored) return;
      const live = a.state.live.trim();
      if (gotAny && live) a.heard(live); else a.silence();
    };
    rec = r;
    try { r.start(); } catch { hooks.onMicError("Mic nahi chala. Neeche likhkar boliye."); }
  };

  const loadBill = async () => {
    billLoading = true;
    try {
      const lines = await (await fetch("/sample_bill.ocr.json")).json();
      a.billOcr(lines, 1080, 1440);
    } finally { billLoading = false; }
  };

  const unsub = a.subscribe((s) => {
    if (s.listen && !wasListening) listen();
    if (!s.listen && wasListening) stopRec();
    wasListening = s.listen;
    if (s.say && s.say.id !== lastSay) { lastSay = s.say.id; speak(s.say); }
    if (s.needBill && !billLoading) void loadBill();
  });
  if (TTS_ON) speechSynthesis.onvoiceschanged = () => { hindiVoice = undefined; };

  return () => { unsub(); stopRec(); clearTimeout(fallback); if (TTS_ON) speechSynthesis.cancel(); };
}

/** Stop any speech right now (reset button). */
export function hush() { if (TTS_ON) speechSynthesis.cancel(); }
