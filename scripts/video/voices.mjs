// Lists the premade voices this account can use (names, ids, labels only).
import { ElevenLabsClient } from "@elevenlabs/elevenlabs-js";
import { loadEnv } from "./env.mjs";

loadEnv();
const client = new ElevenLabsClient();
const res = await client.voices.getAll();
for (const v of res.voices) {
  if (v.category !== "premade") continue;
  const l = v.labels ?? {};
  console.log([v.voiceId, v.name, l.gender, l.age, l.accent, l.description ?? l.descriptive, l.use_case ?? l.use_case].filter(Boolean).join(" | "));
}
