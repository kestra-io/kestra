import {describe, expect, it} from "vitest"
import {splitValidationErrors} from "../../../src/utils/validationErrors"

describe("splitValidationErrors", () => {
    it("returns a single message as a one-element array", () => {
        expect(splitValidationErrors("This is a single message"))
            .toEqual(["This is a single message"])
    })

    it("splits multiple messages separated by newlines", () => {
        expect(splitValidationErrors("Message 1\nMessage 2\nMessage 3"))
            .toEqual(["Message 1", "Message 2", "Message 3"])
    })

    it("handles Windows-style line endings", () => {
        expect(splitValidationErrors("Message 1\r\nMessage 2\r\nMessage 3"))
            .toEqual(["Message 1", "Message 2", "Message 3"])
    })

    it("trims surrounding whitespace from each message", () => {
        expect(splitValidationErrors(" Message 1 \n Message 2 \n Message 3 "))
            .toEqual(["Message 1", "Message 2", "Message 3"])
    })

    it("drops blank lines", () => {
        expect(splitValidationErrors("Message 1\n\nMessage 2\n\nMessage 3"))
            .toEqual(["Message 1", "Message 2", "Message 3"])
    })

    it("keeps commas within a message intact", () => {
        expect(splitValidationErrors('Unrecognized field "name", expected "username"'))
            .toEqual(['Unrecognized field "name", expected "username"'])
    })

    it("returns an empty array when the input is undefined", () => {
        expect(splitValidationErrors(undefined))
            .toEqual([])
    })
})