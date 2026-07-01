import {describe, test, expect} from "vitest"
import {resolveStateGroups, STATE_GROUPS} from "../../../../src/components/Data/KsDataTable/filter/utils/stateGroups"
import {STATES} from "../../../../src/utils/state"

const toOptions = (values: string[]) => values.map(v => ({value: v, label: v}))
const ALL_STATES = Object.keys(STATES)

describe("resolveStateGroups", () => {
    test("buckets every execution state into the documented taxonomy group", () => {
        const groups = resolveStateGroups(toOptions(ALL_STATES))
        const byKey = Object.fromEntries(groups.map(g => [g.key, g.options.map(o => o.value)]))

        expect(byKey.running).toEqual(["SUBMITTED", "CREATED", "RESTARTED", "QUEUED", "RUNNING", "RETRYING"])
        expect(byKey.paused).toEqual(["PAUSED", "BREAKPOINT"])
        expect(byKey.completed).toEqual(["SUCCESS", "WARNING", "SKIPPED", "RETRIED"])
        expect(byKey.failed).toEqual(["FAILED", "KILLING", "KILLED", "CANCELLED"])
    })

    test("keeps the taxonomy order and omits groups that have no present state", () => {
        const groups = resolveStateGroups(toOptions(["RUNNING", "SUCCESS"]))
        expect(groups.map(g => g.key)).toEqual(["running", "completed"])
    })

    test("collects states outside the taxonomy into a trailing 'other' group", () => {
        const groups = resolveStateGroups(toOptions(["RUNNING", "CUSTOM_A", "CUSTOM_B"]))
        const last = groups.at(-1)!
        expect(last.key).toBe("other")
        expect(last.options.map(o => o.value)).toEqual(["CUSTOM_A", "CUSTOM_B"])
    })

    test("taxonomy stays in sync with the canonical state list", () => {
        const taxonomyStates = STATE_GROUPS.flatMap(g => g.states).sort()
        expect(taxonomyStates).toEqual([...ALL_STATES].sort())
    })
})
