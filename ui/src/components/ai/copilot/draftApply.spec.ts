import {describe, it, expect, vi, beforeEach} from "vitest"
import {KestraProblemError} from "@kestra-io/kestra-sdk"
import {alertError, confirmApply, isAlreadyExists, parseArtefactYaml} from "./draftApply"

const {confirmBox, alertBox} = vi.hoisted(() => ({confirmBox: vi.fn(), alertBox: vi.fn()}))
vi.mock("@kestra-io/design-system", () => ({KsMessageBox: {confirm: confirmBox, alert: alertBox}}))

const ENTITY_ALREADY_EXISTS = "https://kestra.io/docs/api-reference/problems/entity-already-exists"
const VALIDATION_FAILED = "https://kestra.io/docs/api-reference/problems/validation-failed"

const problemDocument = (type: string, detail?: string) => ({
    type,
    title: "Problem title",
    status: 409,
    ...(detail === undefined ? {} : {detail}),
})

const axiosLike = (message: string, data: unknown) => Object.assign(new Error(message), {response: {data}})

const t = (key: string) => key

describe("parseArtefactYaml", () => {
    it("extracts the namespace and id from a valid flow", () => {
        expect(parseArtefactYaml("id: my-flow\nnamespace: company.team\n")).toEqual({
            namespace: "company.team",
            id: "my-flow",
        })
    })

    it("returns an empty namespace when the document has none", () => {
        expect(parseArtefactYaml("id: my-flow\n")).toEqual({namespace: "", id: "my-flow"})
    })

    it("returns an empty id when the document has none", () => {
        expect(parseArtefactYaml("namespace: company.team\n")).toEqual({namespace: "company.team", id: ""})
    })

    it("returns empty strings for malformed YAML instead of throwing", () => {
        expect(() => parseArtefactYaml("id: [unclosed\nnamespace: company.team")).not.toThrow()
        expect(parseArtefactYaml("id: [unclosed\nnamespace: company.team")).toEqual({namespace: "", id: ""})
    })
})

describe("isAlreadyExists", () => {
    it("recognises an entity-already-exists problem", () => {
        const error = new KestraProblemError(problemDocument(ENTITY_ALREADY_EXISTS, "A flow already exists."), 409)
        expect(isAlreadyExists(error)).toBe(true)
    })

    it("rejects a different problem type even when its detail says 'already exists'", () => {
        const error = new KestraProblemError(
            problemDocument(VALIDATION_FAILED, "A task referencing a flow that already exists is not allowed."),
            422,
        )
        expect(isAlreadyExists(error)).toBe(false)
    })

    it("rejects a plain Error whose message says 'already exists'", () => {
        expect(isAlreadyExists(new Error("A flow with id 'my-flow' already exists."))).toBe(false)
    })

    it("rejects non-error values", () => {
        expect(isAlreadyExists("A flow already exists.")).toBe(false)
        expect(isAlreadyExists(undefined)).toBe(false)
    })
})

describe("alertError", () => {
    beforeEach(() => {
        vi.clearAllMocks()
        alertBox.mockResolvedValue(undefined)
    })

    it("shows the server problem detail in preference to Error.message", async () => {
        const error = axiosLike(
            "Request failed with status code 422",
            problemDocument(VALIDATION_FAILED, "Task 'my-task' has an unknown type."),
        )

        await alertError(error, "Could not apply the draft.", "Apply failed")

        expect(alertBox).toHaveBeenCalledWith("Task 'my-task' has an unknown type.", "Apply failed", {type: "error"})
    })

    it("shows Error.message when the problem carries no detail", async () => {
        const error = axiosLike("Request failed with status code 500", problemDocument(VALIDATION_FAILED))

        await alertError(error, "Could not apply the draft.", "Apply failed")

        expect(alertBox).toHaveBeenCalledWith("Request failed with status code 500", "Apply failed", {type: "error"})
    })

    it("shows Error.message for a plain Error that carries no problem document", async () => {
        await alertError(new Error("Network timeout"), "Could not apply the draft.", "Apply failed")

        expect(alertBox).toHaveBeenCalledWith("Network timeout", "Apply failed", {type: "error"})
    })

    it("shows the caller's fallback when the error carries neither a detail nor a message", async () => {
        await alertError("network down", "Could not apply the draft.", "Apply failed")

        expect(alertBox).toHaveBeenCalledWith("Could not apply the draft.", "Apply failed", {type: "error"})
    })
})

describe("confirmApply", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("resolves true when the user confirms, with the translated labels and a warning dialog", async () => {
        confirmBox.mockResolvedValue(undefined)

        await expect(confirmApply(t, "Replace the flow?", "Apply draft")).resolves.toBe(true)

        expect(confirmBox).toHaveBeenCalledWith("Replace the flow?", "Apply draft", {
            type: "warning",
            confirmButtonText: "ai.copilot.draft.apply",
            cancelButtonText: "cancel",
        })
    })

    it("resolves false when the dialog is dismissed rather than rejecting", async () => {
        confirmBox.mockRejectedValue("cancel")

        await expect(confirmApply(t, "Replace the flow?", "Apply draft")).resolves.toBe(false)
    })
})
