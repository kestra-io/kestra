import {describe, expect, it} from "vitest"
import {escapePebbleLiteral} from "./pebbleExpression"

describe("escapePebbleLiteral", () => {
    it("returns a value with no quote unchanged", () => {
        expect(escapePebbleLiteral("my-kv-key")).toBe("my-kv-key")
    })

    it("escapes a single quote", () => {
        expect(escapePebbleLiteral("it's")).toBe("it\\'s")
    })

    it("escapes every quote in a value with several", () => {
        expect(escapePebbleLiteral("''it's''")).toBe("\\'\\'it\\'s\\'\\'")
    })

    it("leaves a backslash alone rather than doubling it", () => {
        expect(escapePebbleLiteral("a\\b")).toBe("a\\b")
        expect(escapePebbleLiteral("C:\\Users\\Admin")).toBe("C:\\Users\\Admin")
    })

    it("keeps the original backslash and gains the escape when a backslash precedes a quote", () => {
        expect(escapePebbleLiteral("dir\\'evil.sql")).toBe("dir\\\\'evil.sql")
    })

    it("returns an empty string unchanged", () => {
        expect(escapePebbleLiteral("")).toBe("")
    })
})

