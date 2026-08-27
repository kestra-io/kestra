import {describe, test, expect, vi, beforeEach} from "vitest"
import {copyToClipboard} from "../../../src/utils/clipboard"

describe("copyToClipboard", () => {
    beforeEach(() => {
        document.body.innerHTML = ""
    })

    test("uses navigator.clipboard.writeText when the Clipboard API is available", async () => {
        const writeText = vi.fn().mockResolvedValue(undefined)
        Object.defineProperty(navigator, "clipboard", {
            value: {writeText},
            configurable: true,
        })

        await copyToClipboard("hello")

        expect(writeText).toHaveBeenCalledWith("hello")
    })

    test("falls back to a hidden textarea and execCommand when navigator.clipboard is undefined", async () => {
        // navigator.clipboard is undefined in non-secure contexts (plain HTTP)
        Object.defineProperty(navigator, "clipboard", {
            value: undefined,
            configurable: true,
        })
        let selectedValue: string | undefined
        const execCommand = vi.fn().mockImplementation((command: string) => {
            if (command === "copy") {
                selectedValue = document.querySelector("textarea")?.value
            }
            return true
        })
        document.execCommand = execCommand

        await copyToClipboard("fallback text")

        expect(execCommand).toHaveBeenCalledWith("copy")
        expect(selectedValue).toBe("fallback text")
        // The helper cleans up the temporary textarea
        expect(document.querySelector("textarea")).toBeNull()
    })
})
