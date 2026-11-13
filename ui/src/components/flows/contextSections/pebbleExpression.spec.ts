import {describe, expect, it} from "vitest"
import {escapePebbleLiteral} from "./pebbleExpression"

describe("escapePebbleLiteral", () => {
    it("returns a value with no quote unchanged", () => {
        expect(escapePebbleLiteral("my_key")).toBe("my_key")
        expect(escapePebbleLiteral("sample-value-123")).toBe("sample-value-123")
    })

    it("escapes a single quote", () => {
        expect(escapePebbleLiteral("user's_key")).toBe("user\\'s_key")
        expect(escapePebbleLiteral("it's")).toBe("it\\'s")
    })

    it("escapes every quote in a value with several", () => {
        expect(escapePebbleLiteral("it's a user's 'quoted' value")).toBe("it\\'s a user\\'s \\'quoted\\' value")
        expect(escapePebbleLiteral("'''")).toBe("\\'\\'\\'")
    })

    it("leaves a backslash alone rather than doubled", () => {
        expect(escapePebbleLiteral("path\\to\\file")).toBe("path\\to\\file")
        expect(escapePebbleLiteral("C:\\Windows\\System32")).toBe("C:\\Windows\\System32")
    })

    it("keeps backslash and gains escape when backslash is already followed by a quote", () => {
        expect(escapePebbleLiteral("prefix\\'suffix")).toBe("prefix\\\\'suffix")
        expect(escapePebbleLiteral("\\'")).toBe("\\\\'")
    })

    it("returns an empty string unchanged", () => {
        expect(escapePebbleLiteral("")).toBe("")
    })
})
