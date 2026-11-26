// Batao Kaise voice engine (pane 1 owns this folder). The UI only does I/O:
//  - speak state.say.text (speechSynthesis, hi-IN, rate 0.9, or 0.7 if say.slow), then call a.spoken(say.id)
//  - when state.listen becomes true: cancel speech, start recognition (hi-IN, interim) -> a.partial / a.heard / a.silence / a.noMatch
//  - when state.needBill becomes true: fetch /sample_bill.ocr.json and call a.billOcr(lines, 1080, 1440)
//  - render state.view; report taps with a.payTap(id), a.payType(id, value), a.billTap(kind), a.share(target), a.editRescue(text), a.undoLast()
export { Assistant, createAssistant } from "./assistant";
export type { State, View, Say, Phase, PayScreen, Picture, ShareTarget, Options } from "./assistant";
export { FIELD_LABEL, FIELD_COLOR } from "./bill";
export type { BillField, FieldKind, OcrLine } from "./bill";
export { redact } from "./redact";
export { normalize, spokenForm } from "./lang";
export { detect, describe } from "./intent";
export { TEST_PHRASES, runPhrases } from "./phrases";
