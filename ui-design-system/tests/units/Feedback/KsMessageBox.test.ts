import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import * as ElementPlus from "element-plus"
import {KsMessageBox} from "../../../src/components/Feedback/KsMessageBox"

describe("KsMessageBox", () => {
    beforeEach(() => {
        vi.spyOn(ElementPlus, "ElMessageBox").mockResolvedValue("confirm" as any)
        ;(ElementPlus.ElMessageBox as any).alert = vi.fn().mockResolvedValue("confirm")
        ;(ElementPlus.ElMessageBox as any).confirm = vi.fn().mockResolvedValue("confirm")
        ;(ElementPlus.ElMessageBox as any).prompt = vi.fn().mockResolvedValue({value: "input", action: "confirm"})
        ;(ElementPlus.ElMessageBox as any).close = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("is callable as a function with options object", () => {
        KsMessageBox({title: "Confirm", message: "Are you sure?"})
        expect(ElementPlus.ElMessageBox).toHaveBeenCalledWith({title: "Confirm", message: "Are you sure?"})
    })

    test("KsMessageBox.confirm delegates to ElMessageBox.confirm", () => {
        KsMessageBox.confirm("Delete item?", "Confirmation", {type: "warning"})
        expect(ElementPlus.ElMessageBox.confirm).toHaveBeenCalledWith(
            "Delete item?",
            "Confirmation",
            {type: "warning"},
        )
    })

    test("KsMessageBox.alert delegates to ElMessageBox.alert", () => {
        KsMessageBox.alert("Read this", "Notice")
        expect(ElementPlus.ElMessageBox.alert).toHaveBeenCalledWith("Read this", "Notice")
    })

    test("KsMessageBox.prompt delegates to ElMessageBox.prompt", () => {
        KsMessageBox.prompt("Enter value", "Input")
        expect(ElementPlus.ElMessageBox.prompt).toHaveBeenCalledWith("Enter value", "Input")
    })

    test("KsMessageBox.close delegates to ElMessageBox.close", () => {
        KsMessageBox.close()
        expect(ElementPlus.ElMessageBox.close).toHaveBeenCalled()
    })

    test("KsMessageBox.confirm returns a promise", () => {
        const result = KsMessageBox.confirm("Sure?", "Title")
        expect(result).toBeInstanceOf(Promise)
    })
})
