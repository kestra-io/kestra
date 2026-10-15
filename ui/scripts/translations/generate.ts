/**
 * OSS entry point for the translation generator.
 *
 * The implementation lives in `./generateTranslations.ts` and is shared with EE
 * (`kestra-ee/ui-ee/scripts/translations/generate.ts`); this file only supplies what is
 * OSS-specific — the Gemini client, the translations directory, and the design-system
 * `*.locale.ts` files, which exist only in this repo.
 *
 * Runs from anywhere — from `ui/` via `npm run translations:generate`, or from the repository root:
 *   GEMINI_API_KEY=... node --experimental-strip-types ui/scripts/translations/generate.ts [true]
 *
 * Keys whose English source has changed since they were last translated are detected from the
 * fingerprint files below and re-translated automatically. The optional positional "true" forces a
 * full re-translation of every key, ignoring those fingerprints.
 *
 * Requires the `@google/genai` package and Node 22+ (for native TypeScript type stripping and fs.globSync).
 */
import {globSync} from "node:fs"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {GoogleGenAI} from "@google/genai"
import {generateTranslations, translateLocaleFiles} from "./generateTranslations.ts"

const here = dirname(fileURLToPath(import.meta.url))
const uiRoot = resolve(here, "../..")

const client = new GoogleGenAI({apiKey: process.env.GEMINI_API_KEY})

const force = process.argv[2]?.toLowerCase() === "true"

// Both phases run their units concurrently. Each language owns exactly one output file and each
// locale file is independent, so nothing is shared but the request gate, which bounds how many
// translations are in flight at once. Phase 1 is awaited before phase 2 only to keep the log
// readable; the two are otherwise independent.

// 1. Translate the per-language JSON files from en.json.
await generateTranslations({
    client,
    translationsDir: resolve(uiRoot, "src/translations"),
    fingerprintsFile: resolve(here, "fingerprints.json"),
    force,
})

// 2. Translate the design-system `*.locale.ts` files (each holds every language in one file).
await translateLocaleFiles({
    client,
    localeFiles: globSync(resolve(uiRoot, "packages/design-system/**/*.locale.ts")),
    fingerprintsFile: resolve(here, "fingerprints-design-system.json"),
    force,
})
