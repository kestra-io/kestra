import {describe, expect, it} from "vitest"
import {dirname, resolve} from "node:path"
import {fileURLToPath} from "node:url"
import {countByFile, findDeepSelectors} from "./deepSelectorGuard"

const UI = resolve(dirname(fileURLToPath(import.meta.url)), "../../..")

/** The only deep selectors left in feature code: how many lines each file holds, and why. */
const ALLOWED: Record<string, {lines: number; reason: string}> = {
    "src/components/logs/LogLine.vue": {lines: 2, reason: "styles the log message it renders itself with v-html"},
    "src/components/no-code/styles/code.css": {lines: 2, reason: "reaches the inputs of the task components that render inside the shared no-code wrapper"},
    "src/components/SurveyDialog.vue": {lines: 3, reason: "draws a ring radio and a larger checkbox that KsRadio and KsCheckbox do not offer yet"},
    "src/components/executions/Logs.vue": {lines: 1, reason: "the border sits between two recycle-scroller items, and on the item it would change the measured row height"},
    "packages/topology/src/Topology.vue": {lines: 9, reason: "styles the vue-flow nodes and edges the topology wraps"},
}

describe("deep selectors", () => {
    const hits = findDeepSelectors(UI, [resolve(UI, "src"), resolve(UI, "packages/topology/src")])
    const counts = countByFile(hits)

    it("adds no :deep() to feature code", () => {
        const offenders = hits.filter((hit) => {
            const file = hit.slice(0, hit.lastIndexOf(":"))
            return counts[file] > (ALLOWED[file]?.lines ?? 0)
        })
        expect(
            offenders,
            `Style a child through a class on its root, a prop, or a --ks-* variable instead of :deep() (see ui/AGENTS.md):\n${offenders.join("\n")}`,
        ).toEqual([])
    })

    it("lists each allowed file with its current number of deep selectors", () => {
        const stale = Object.entries(ALLOWED).filter(([file, {lines}]) => (counts[file] ?? 0) !== lines).map(([file]) => file)
        expect(stale, "Update or remove these entries in ALLOWED").toEqual([])
    })
})
