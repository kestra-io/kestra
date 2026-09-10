import {describe, expect, test} from "vitest"
import * as YamlUtils from "../../../../src/utils/yaml/paths.ts"

describe("parsePath", () => {
    test("parses a bare key", () => {
        expect(YamlUtils.parsePath("tasks")).toEqual(["tasks"])
    })

    test("parses a key followed by an index", () => {
        expect(YamlUtils.parsePath("tasks[0]")).toEqual(["tasks", 0])
    })

    test("parses nested keys and indices", () => {
        expect(YamlUtils.parsePath("tasks[0].then[1]")).toEqual(["tasks", 0, "then", 1])
    })

    test("parses a bracket-quoted key that contains a dot as a single segment", () => {
        expect(YamlUtils.parsePath("tasks[0].cases[\"eu.prod\"][1]")).toEqual(["tasks", 0, "cases", "eu.prod", 1])
    })

    test("keeps a quoted numeric key a string, distinct from a numeric index", () => {
        expect(YamlUtils.parsePath("cases[\"0\"]")).toEqual(["cases", "0"])
        expect(YamlUtils.parsePath("cases[0]")).toEqual(["cases", 0])
    })

    test("round-trips a path through joinPath", () => {
        const path = "tasks[0].cases[\"eu.prod\"][1]"
        expect(YamlUtils.joinPath(YamlUtils.parsePath(path))).toBe(path)
    })
})

describe("appendKeyToPath", () => {
    test("appends a simple key with a dot separator", () => {
        expect(YamlUtils.appendKeyToPath("tasks[0].cases", "prod")).toBe("tasks[0].cases.prod")
    })

    test("bracket-quotes a key containing a dot", () => {
        expect(YamlUtils.appendKeyToPath("tasks[0].cases", "eu.prod")).toBe("tasks[0].cases[\"eu.prod\"]")
    })

    test("bracket-quotes a key containing brackets", () => {
        expect(YamlUtils.appendKeyToPath("tasks[0].cases", "a[1]")).toBe("tasks[0].cases[\"a[1]\"]")
    })
})
