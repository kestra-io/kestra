import {describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {findRawDateFormatting} from "./dateFormatGuard"

const SRC = resolve(dirname(fileURLToPath(import.meta.url)), "../../src")

/** Files that format a date on purpose without `dateUtils`, each with the reason. */
const ALLOWED: Record<string, string> = {
    "utils/submitTask.ts": "serializes DATE and TIME inputs for the API, nothing is displayed",
    "components/flows/Curl.vue": "serializes DATE and TIME inputs into the cURL command",
    "components/kv/kvValue.ts": "already applies the Settings timezone (kestra-io/kestra#9428)",
}

describe("date formatting", () => {
    const hits = findRawDateFormatting(SRC)

    it("formats displayed dates with dateUtils.dateFilter across ui/src", () => {
        const offenders = hits.filter((hit) => !(hit.split(":")[0] in ALLOWED))
        expect(
            offenders,
            `Format dates with dateUtils.dateFilter so the timezone and date format from Settings apply (see ui/AGENTS.md):\n${offenders.join("\n")}`,
        ).toEqual([])
    })

    it("lists only allowed files that still format a date themselves", () => {
        const stale = Object.keys(ALLOWED).filter((file) => !hits.some((hit) => hit.startsWith(`${file}:`)))
        expect(stale, "Remove these from ALLOWED").toEqual([])
    })
})
