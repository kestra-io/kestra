import {describe, expect, it} from "vitest"
import {prettyTypeLiteral, splitTopLevelMembers} from "./generate-flat-dts"

describe("splitTopLevelMembers", () => {
    it("should keep a function-typed member separate from the members after it", () => {
        const members = splitTopLevelMembers(
            "{ source?: string; fetchOutputs?: (() => Promise<Record<string, unknown>>) | undefined; tenant?: string; }",
        )

        expect(members).toEqual([
            "source?: string",
            "fetchOutputs?: (() => Promise<Record<string, unknown>>) | undefined",
            "tenant?: string",
        ])
    })

    it("should not split on a semicolon nested in a member's own type", () => {
        expect(splitTopLevelMembers("{ progress: { step: string; taskRunId: string; }[]; taskType: string; }")).toEqual([
            "progress: { step: string; taskRunId: string; }[]",
            "taskType: string",
        ])
    })
})

describe("prettyTypeLiteral", () => {
    it("should keep a non-literal type such as an empty zod object's Record as is", () => {
        expect(prettyTypeLiteral("Record<string, never>", "    ")).toBe("Record<string, never>")
    })
})
