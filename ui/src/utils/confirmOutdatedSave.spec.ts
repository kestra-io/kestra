import {beforeEach, describe, expect, it, vi} from "vitest"
import type {Mock} from "vitest"
import type {VNode} from "vue"
import {KsMessageBox} from "@kestra-io/design-system"
import {confirmOutdatedSave} from "./confirmOutdatedSave"

vi.mock("@kestra-io/design-system", () => ({KsMessageBox: vi.fn()}))

const messageBox = KsMessageBox as unknown as Mock
const t = (key: string) => key

const optionsOfLastCall = () => messageBox.mock.calls[0][0] as Record<string, unknown>

describe("confirmOutdatedSave", () => {
    beforeEach(() => {
        messageBox.mockReset()
    })

    it("resolves true when the user confirms", async () => {
        messageBox.mockResolvedValue("confirm")

        await expect(confirmOutdatedSave(t, "message")).resolves.toBe(true)
    })

    it("resolves false rather than rejecting when the user cancels", async () => {
        messageBox.mockRejectedValue("cancel")

        await expect(confirmOutdatedSave(t, "message")).resolves.toBe(false)
    })

    it.each([
        ["close", "close"],
        ["an error", new Error("dismissed")],
        ["undefined", undefined],
    ])("resolves false when the box is dismissed any other way (%s)", async (_label, reason) => {
        messageBox.mockRejectedValue(reason)

        await expect(confirmOutdatedSave(t, "message")).resolves.toBe(false)
    })

    it("takes the title and the button labels from the translation function", async () => {
        messageBox.mockResolvedValue("confirm")

        await confirmOutdatedSave(t, "message")

        expect(optionsOfLastCall()).toMatchObject({
            title: "outdated revision save confirmation.update.title",
            confirmButtonText: "ok",
            cancelButtonText: "cancel",
        })
    })

    it("renders the supplied message in the body", async () => {
        messageBox.mockResolvedValue("confirm")

        await confirmOutdatedSave(t, "Someone else changed this flow")

        const body = (optionsOfLastCall().message as () => VNode)()
        expect(body.type).toBe("p")
        expect(body.children).toBe("Someone else changed this flow")
    })

    it("shows a cancel button, no close icon and is not centred", async () => {
        messageBox.mockResolvedValue("confirm")

        await confirmOutdatedSave(t, "message")

        expect(optionsOfLastCall()).toMatchObject({
            showCancelButton: true,
            showClose: false,
            center: false,
        })
    })
})