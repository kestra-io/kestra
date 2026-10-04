import {describe, test, expect} from "vitest"
import {afterLastDot} from "../../../src/utils/string"

describe("afterLastDot", () => {
    test("returns the short name of a fully qualified class name", () => {
        expect(afterLastDot("io.kestra.plugin.core.log.Log")).toBe("Log")
    })

    test("returns a string with no dot unchanged", () => {
        expect(afterLastDot("Log")).toBe("Log")
    })

    test("returns an empty string for a trailing dot", () => {
        expect(afterLastDot("io.kestra.")).toBe("")
    })

    test("returns an empty string for an empty string", () => {
        expect(afterLastDot("")).toBe("")
    })

    test("returns the rest of the string after a leading dot", () => {
        expect(afterLastDot(".Log")).toBe("Log")
    })
})
