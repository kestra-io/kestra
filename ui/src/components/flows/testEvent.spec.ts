import {describe, expect, it} from "vitest"
import {parseHeaderLines} from "./testEvent"

describe("parseHeaderLines", () => {
    it("parses one Name: value pair per line into matching entries", () => {
        expect(parseHeaderLines("Content-Type: application/json\nX-Custom: hello")).toEqual({
            "Content-Type": "application/json",
            "X-Custom": "hello",
        })
    })

    it("trims surrounding whitespace from the name and the value", () => {
        expect(parseHeaderLines("  Content-Type  :  application/json  ")).toEqual({
            "Content-Type": "application/json",
        })
    })

    it("skips blank and whitespace-only lines", () => {
        expect(parseHeaderLines("Content-Type: application/json\n\n   \nX-Custom: hello")).toEqual({
            "Content-Type": "application/json",
            "X-Custom": "hello",
        })
    })

    it("skips a line with no colon", () => {
        expect(parseHeaderLines("Content-Type: application/json\nno-colon-here\nX-Custom: hello")).toEqual({
            "Content-Type": "application/json",
            "X-Custom": "hello",
        })
    })

    it("skips a line starting with a colon, since the name would be empty", () => {
        expect(parseHeaderLines(": no-name\n   : indented-no-name\nContent-Type: application/json")).toEqual({
            "Content-Type": "application/json",
        })
    })

    it("keeps everything after the first colon when the value contains a colon", () => {
        expect(parseHeaderLines("X-Time: 12:30:45")).toEqual({"X-Time": "12:30:45"})
    })

    it("keeps an empty value as an empty string, and a later line wins over an earlier one with the same name", () => {
        expect(parseHeaderLines("X-Empty:\nX-Dup: first\nX-Dup: second")).toEqual({
            "X-Empty": "",
            "X-Dup": "second",
        })
    })

    it("returns an empty object for empty input", () => {
        expect(parseHeaderLines("")).toEqual({})
    })
})
