// Builds docs/video/batao-kaise-demo.mp4 from out/frames.ffconcat + out/timeline.json + the cached ElevenLabs clips.
import { readFileSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { synth } from "./tts.mjs";

const OUT = new URL("./out/", import.meta.url).pathname;
const DEST = new URL("../../docs/video/", import.meta.url).pathname;
mkdirSync(DEST, { recursive: true });
const { total, events } = JSON.parse(readFileSync(`${OUT}timeline.json`, "utf8"));

const clips = [];
for (const e of events) clips.push({ ...e, ...(await synth(e.role, e.text)) });

// Sanity: voices must not talk over each other.
const sorted = [...clips].sort((a, b) => a.t - b.t);
for (let i = 1; i < sorted.length; i++) {
  const p = sorted[i - 1], c = sorted[i];
  if (p.t + p.ms > c.t + 150) console.warn(`overlap ${((p.t + p.ms - c.t) / 1000).toFixed(2)}s: "${p.text}" -> "${c.text}"`);
}

const GAIN = { app: 1.0, user: 0.95, narrator: 1.0 };
const args = ["-y", "-v", "error", "-f", "concat", "-safe", "0", "-i", `${OUT}frames.ffconcat`];
for (const c of clips) args.push("-i", c.file);
const parts = clips.map((c, i) => `[${i + 1}:a]aresample=44100,aformat=channel_layouts=stereo,volume=${GAIN[c.role]},adelay=${c.t}|${c.t}[a${i}]`);
const mix = `${clips.map((_, i) => `[a${i}]`).join("")}amix=inputs=${clips.length}:normalize=0:dropout_transition=0,apad,atrim=0:${(total / 1000).toFixed(2)},loudnorm=I=-16:TP=-1.5:LRA=11[aout]`;
const video = `[0:v]fps=30,scale=1920:1080:flags=lanczos,format=yuv420p,trim=0:${(total / 1000).toFixed(2)},setpts=PTS-STARTPTS[vout]`; // concat list already starts at t0
args.push("-filter_complex", [...parts, mix, video].join(";"),
  "-map", "[vout]", "-map", "[aout]", "-c:v", "libx264", "-preset", "slow", "-crf", "20", "-c:a", "aac", "-b:a", "160k",
  "-movflags", "+faststart", `${DEST}batao-kaise-demo.mp4`);
execFileSync("ffmpeg", args, { stdio: "inherit" });
const dur = execFileSync("ffprobe", ["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", `${DEST}batao-kaise-demo.mp4`]).toString().trim();
console.log(`wrote docs/video/batao-kaise-demo.mp4 (${parseFloat(dur).toFixed(1)}s, ${clips.length} voice clips)`);
