import {describe, expect, test} from "vitest"

import {matchesLevelFilter, normalizeFilePreview, type FilePreview} from "./executions"

describe("executions store", () => {
    test("keeps Ion preview objects without requiring array helpers", () => {
        const preview: FilePreview = {
            extension: "ion",
            type: "TEXT",
            content: {message: "hello from ship logs", level: "INFO"},
            truncated: false,
        }

        expect(normalizeFilePreview(preview)).toEqual(preview)
    })

    test("keeps the Ion array workaround for scalar content", () => {
        expect(normalizeFilePreview({
            extension: "ion",
            type: "LIST",
            content: ["first", "second"],
            truncated: false,
        })).toEqual({
            extension: "ion",
            type: "TEXT",
            content: "first\nsecond",
            truncated: false,
        })
    })

    test("filters held logs by level the way the backend does", () => {
        expect(matchesLevelFilter("WARN", {value: "INFO", direction: "min"})).toBe(true)
        expect(matchesLevelFilter("DEBUG", {value: "INFO", direction: "min"})).toBe(false)
        expect(matchesLevelFilter("DEBUG", {value: "INFO", direction: "max"})).toBe(true)
        expect(matchesLevelFilter("WARN", {value: "INFO", direction: "max"})).toBe(false)
        expect(matchesLevelFilter("TRACE", {value: "ERROR,TRACE", direction: "in"})).toBe(true)
        expect(matchesLevelFilter("TRACE", {value: "ERROR,TRACE", direction: "not_in"})).toBe(false)
    })
})
