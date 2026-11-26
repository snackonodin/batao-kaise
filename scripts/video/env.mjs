// Loads ELEVENLABS_API_KEY from the repo's .env into process.env without printing anything.
import { readFileSync } from "node:fs";

export function loadEnv() {
  const path = new URL("../../.env", import.meta.url).pathname;
  let text = "";
  try { text = readFileSync(path, "utf8"); } catch { throw new Error(".env not found at repo root"); }
  for (const line of text.split(/\r?\n/)) {
    const m = line.match(/^\s*(?:export\s+)?([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
    if (!m || process.env[m[1]]) continue;
    process.env[m[1]] = m[2].replace(/^(['"])(.*)\1$/, "$2");
  }
  if (!process.env.ELEVENLABS_API_KEY) throw new Error("ELEVENLABS_API_KEY missing in .env");
}
