import {describe, it, expect, vi} from "vitest"

// `testEvent.ts` imports the SDK and the webhook URL helper at module level. `parseHeaderLines` uses
// neither, so both are stubbed to keep this spec independent of them.
vi.mock("@kestra-io/kestra-sdk/executions", () => ({triggerExecutionByPostWebhook: vi.fn()}))
vi.mock("../../utils/webhook", () => ({webhookUrl: () => "http://localhost/api/v1/main/executions/webhook"}))

import {parseHeaderLines} from "./testEvent"

describe("parseHeaderLines", () => {
    it("parses one `Name: value` pair per line", () => {
        expect(parseHeaderLines("X-Api-Key: abc123\nX-Source: unit-test")).toEqual({
            "X-Api-Key": "abc123",
            "X-Source": "unit-test",
        })
    })

    it("trims surrounding whitespace from both the name and the value", () => {
        expect(parseHeaderLines("   X-Api-Key   :    abc123   ")).toEqual({"X-Api-Key": "abc123"})
    })

    it("skips blank and whitespace-only lines", () => {
        expect(parseHeaderLines("\nX-A: 1\n\n   \n\t\nX-B: 2\n")).toEqual({"X-A": "1", "X-B": "2"})
    })

    it("skips a line with no colon", () => {
        expect(parseHeaderLines("not a header\nX-A: 1")).toEqual({"X-A": "1"})
    })

    it("skips a line starting with a colon, since the name would be empty", () => {
        expect(parseHeaderLines(": orphan value\nX-A: 1")).toEqual({"X-A": "1"})
    })

    it("skips a line whose name is only whitespace before the colon", () => {
        expect(parseHeaderLines("   : orphan value\nX-A: 1")).toEqual({"X-A": "1"})
    })

    it("keeps everything after the first colon when the value contains a colon", () => {
        expect(parseHeaderLines("Referer: https://example.com:8080/path?a=b:c")).toEqual({
            "Referer": "https://example.com:8080/path?a=b:c",
        })
    })

    it("keeps an empty value as an empty string", () => {
        expect(parseHeaderLines("X-Empty:\nX-Spaces:    ")).toEqual({"X-Empty": "", "X-Spaces": ""})
    })

    it("lets a later line win over an earlier one with the same name", () => {
        expect(parseHeaderLines("X-A: first\nX-B: other\nX-A: second")).toEqual({"X-A": "second", "X-B": "other"})
    })

    it("handles Windows line endings", () => {
        expect(parseHeaderLines("X-A: 1\r\nX-B: 2\r\n")).toEqual({"X-A": "1", "X-B": "2"})
    })

    it("returns an empty object for an empty input", () => {
        expect(parseHeaderLines("")).toEqual({})
    })

    it("returns an empty object when no line is a valid header", () => {
        expect(parseHeaderLines("  \nno colon here\n: no name")).toEqual({})
    })
})
