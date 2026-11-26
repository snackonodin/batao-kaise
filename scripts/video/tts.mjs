// ElevenLabs text-to-speech with an on-disk cache (out/audio/<hash>.mp3) and measured durations.
import { ElevenLabsClient } from "@elevenlabs/elevenlabs-js";
import { createHash } from "node:crypto";
import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { loadEnv } from "./env.mjs";

export const VOICES = {
  app: { id: "hpp4J3VqNfWAUOO0d1Us", name: "Bella", stability: 0.6, similarity: 0.75 },      // the app: calm, warm, clear
  user: { id: "pqHfZKP75CvOlQylNhV4", name: "Bill", stability: 0.45, similarity: 0.75 },      // older shopkeeper
  narrator: { id: "JBFqnCBsd6RMkjVDRZzb", name: "George", stability: 0.55, similarity: 0.75 }, // narrator for judges
};
const MODEL = "eleven_v4";
const OUT = new URL("./out/audio/", import.meta.url).pathname;

let client;
const c = () => { if (!client) { loadEnv(); client = new ElevenLabsClient(); } return client; };

export const keyOf = (role, text) => createHash("sha1").update(`${MODEL}|${role}|${VOICES[role].id}|${text}`).digest("hex").slice(0, 16);
export const fileOf = (role, text) => `${OUT}${role}-${keyOf(role, text)}.mp3`;

export function durationMs(file) {
  const s = execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", file]).toString().trim();
  return Math.round(parseFloat(s) * 1000);
}

/** Hinglish for the screen, but spoken text can differ (e.g. digits). */
export async function synth(role, text, { lang = "hi" } = {}) {
  mkdirSync(OUT, { recursive: true });
  // Digit groups like "4-5-1-2" pause at every hyphen; spaces read the same digits at a natural pace.
  const say = text.replace(/(\d)-(?=\d)/g, "$1 ");
  const file = fileOf(role, say);
  if (!existsSync(file)) {
    const v = VOICES[role];
    const stream = await c().textToSpeech.convert(v.id, {
      text: say, modelId: MODEL, languageCode: lang, outputFormat: "mp3_44100_128",
      voiceSettings: { stability: v.stability, similarityBoost: v.similarity },
    });
    const chunks = [];
    for await (const ch of stream) chunks.push(Buffer.from(ch));
    writeFileSync(file, Buffer.concat(chunks));
  }
  return { file, ms: durationMs(file) };
}
