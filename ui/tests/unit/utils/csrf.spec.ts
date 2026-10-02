import {afterEach, describe, expect, it} from "vitest"
import {getCsrfToken} from "../../../src/utils/csrf"

describe("getCsrfToken", () => {
    afterEach(() => {
        document.head.innerHTML = ""
    })

    it("returns the content of the csrf-token meta tag when present", () => {
        document.head.innerHTML = "<meta name=\"csrf-token\" content=\"abc123\">"

        expect(getCsrfToken()).toBe("abc123")
    })

    it("returns null when the csrf-token meta tag is absent", () => {
        document.head.innerHTML = ""

        expect(getCsrfToken()).toBeNull()
    })

    it("returns null when the csrf-token meta tag has no content attribute", () => {
        document.head.innerHTML = "<meta name=\"csrf-token\">"

        expect(getCsrfToken()).toBeNull()
    })

    it("does not pick up an unrelated meta tag", () => {
        document.head.innerHTML = "<meta name=\"viewport\" content=\"width=device-width\">" +
            "<meta name=\"description\" content=\"not-a-token\">"

        expect(getCsrfToken()).toBeNull()
    })
})
