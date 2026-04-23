import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {ElMessageBox} from "element-plus"
import {KsMessageBox} from "../../../src/components/Feedback/KsMessageBox"

vi.mock("element-plus", () => ({
    ElMessageBox: Object.assign(
        vi.fn(),
        {
            alert: vi.fn(),
            confirm: vi.fn(),
            prompt: vi.fn(),
            close: vi.fn(),
        },
    ),
}))

describe("KsMessageBox", () => {
    beforeEach(() => {
        vi.mocked(ElMessageBox).mockResolvedValue("confirm" as any)
        vi.mocked(ElMessageBox.alert).mockResolvedValue("confirm" as any)
        vi.mocked(ElMessageBox.confirm).mockResolvedValue("confirm" as any)
        vi.mocked(ElMessageBox.prompt).mockResolvedValue({value: "input", action: "confirm"} as any)
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    test("is callable as a function with options object", () => {
        KsMessageBox({title: "Confirm", message: "Are you sure?"})
        expect(ElMessageBox).toHaveBeenCalledWith({title: "Confirm", message: "Are you sure?"})
    })

    test("KsMessageBox.confirm delegates to ElMessageBox.confirm", () => {
        KsMessageBox.confirm("Delete item?", "Confirmation", {type: "warning"})
        expect(ElMessageBox.confirm).toHaveBeenCalledWith(
            "Delete item?",
            "Confirmation",
            {type: "warning"},
        )
    })

    test("KsMessageBox.alert delegates to ElMessageBox.alert", () => {
        KsMessageBox.alert("Read this", "Notice")
        expect(ElMessageBox.alert).toHaveBeenCalledWith("Read this", "Notice")
    })

    test("KsMessageBox.prompt delegates to ElMessageBox.prompt", () => {
        KsMessageBox.prompt("Enter value", "Input")
        expect(ElMessageBox.prompt).toHaveBeenCalledWith("Enter value", "Input")
    })

    test("KsMessageBox.close delegates to ElMessageBox.close", () => {
        KsMessageBox.close()
        expect(ElMessageBox.close).toHaveBeenCalled()
    })

    test("KsMessageBox.confirm returns a promise", () => {
        const result = KsMessageBox.confirm("Sure?", "Title")
        expect(result).toBeInstanceOf(Promise)
    })
})
