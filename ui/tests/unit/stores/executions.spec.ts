import {describe, expect, test} from "vitest"

import {normalizeFilePreview, type FilePreview} from "../../../src/stores/executions"

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
})
