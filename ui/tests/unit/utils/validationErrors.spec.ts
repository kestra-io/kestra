import {describe, expect, it} from "vitest"
import {errorsByFieldPath, fieldPathOf, hasErrorUnder} from "../../../src/utils/validationErrors"

describe("fieldPathOf", () => {
    it("should render list indices in brackets and keys in dots", () => {
        expect(fieldPathOf(["options", "headers", "2", "name"])).toBe("options.headers[2].name")
    })

    it("should fall back to the yaml key of a renamed java property", () => {
        expect(fieldPathOf(["_finally"])).toBe("finally")
    })
})

describe("errorsByFieldPath", () => {
    const errors = [
        {detail: "must not be null", pointer: "/tasks/0/message"},
        {detail: "must match \"^[a-z]+$\"", pointer: "/tasks/0/message"},
        {detail: "must not be empty", pointer: "/tasks/1/message"},
        {detail: "Unable to validate the flow: boom"},
    ]

    it("should key the errors of one block by the field they address", () => {
        expect(errorsByFieldPath(errors, ["tasks", 0])).toEqual(
            new Map([["message", ["must not be null", "must match \"^[a-z]+$\""]]]),
        )
    })

    it("should key flow-level errors by their own field", () => {
        expect(errorsByFieldPath([{detail: "must not be null", pointer: "/namespace"}]))
            .toEqual(new Map([["namespace", ["must not be null"]]]))
    })

    it("should drop an error that only addresses the block itself", () => {
        expect(errorsByFieldPath([{detail: "boom", pointer: "/tasks/0"}], ["tasks", 0])).toEqual(new Map())
    })

    it("should match a prefix written with the yaml key of a renamed java property", () => {
        expect(errorsByFieldPath([{detail: "must not be null", pointer: "/_finally/0/message"}], ["finally", 0]))
            .toEqual(new Map([["message", ["must not be null"]]]))
    })
})

describe("hasErrorUnder", () => {
    const errors = new Map([["options.headers[0].name", ["must not be null"]]])

    it("should report an error sitting below the path", () => {
        expect(hasErrorUnder(errors, "options")).toBe(true)
        expect(hasErrorUnder(errors, "options.headers")).toBe(true)
        expect(hasErrorUnder(errors, "options.headers[0].name")).toBe(true)
    })

    it("should not report a path that only shares a prefix with it", () => {
        expect(hasErrorUnder(errors, "option")).toBe(false)
        expect(hasErrorUnder(errors, "options.header")).toBe(false)
    })
})

describe("errorsByFieldPath through a wrapper", () => {
    it("should key a dag task's errors by the field, not by the wrapper", () => {
        expect(errorsByFieldPath(
            [{detail: "must not be null", pointer: "/tasks/0/tasks/0/task/message"}],
            ["tasks", 0, "tasks", 0, "task"],
        )).toEqual(new Map([["message", ["must not be null"]]]))
    })

    it("should key nothing by the field when the wrapper segment is missing from the prefix", () => {
        expect(errorsByFieldPath(
            [{detail: "must not be null", pointer: "/tasks/0/tasks/0/task/message"}],
            ["tasks", 0, "tasks", 0],
        )).toEqual(new Map([["task.message", ["must not be null"]]]))
    })
})
