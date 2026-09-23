import {describe, it, expect} from "vitest"
import fs from "node:fs"
import path from "node:path"
import * as State from "../../../packages/design-system/src/utils/state"

const CORE_STATE = path.resolve(__dirname, "../../../../core/src/main/java/io/kestra/core/models/flows/State.java")

const terminatedFromCore = () => {
    const source = fs.readFileSync(CORE_STATE, "utf-8")
    // State.java declares isTerminated() twice: the outer one delegates. Anchor on the
    // enum's, without consuming the first `Type.` of its body.
    const body = source.match(/public boolean isTerminated\(\) \{\s*return this == (Type\.[\s\S]*?);/)![1]
    return [...body.matchAll(/Type\.([A-Z_]+)/g)].map(match => match[1])
}

describe("State.isTerminated", () => {
    it("agrees with the backend, which owns the definition", () => {
        const fromCore = terminatedFromCore()

        expect(fromCore).toEqual(["FAILED", "WARNING", "SUCCESS", "KILLED", "CANCELLED", "RETRIED", "SKIPPED", "RESUBMITTED"])
        expect(fromCore.filter(state => !State.isTerminated(state))).toEqual([])
    })

    it("does not stop on a state the execution is still on its way out of", () => {
        expect(State.isTerminated(State.QUEUED)).toBe(false)
        expect(State.isTerminated(State.RETRYING)).toBe(false)
        expect(State.isTerminated(State.RESTARTED)).toBe(false)
        expect(State.isTerminated(State.RUNNING)).toBe(false)
        expect(State.isTerminated(State.PAUSED)).toBe(false)
        expect(State.isTerminated(State.BREAKPOINT)).toBe(false)
    })

    it("is a different question from being non-running", () => {
        expect(State.getNonRunningStates()).toContain(State.QUEUED)
        expect(State.isTerminated(State.QUEUED)).toBe(false)
    })
})
