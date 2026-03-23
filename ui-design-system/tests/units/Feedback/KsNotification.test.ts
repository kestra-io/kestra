import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {ElNotification} from "element-plus"
import {KsNotification} from "../../../src/components/Feedback/KsNotification"

vi.mock("element-plus", () => ({
    ElNotification: Object.assign(
        vi.fn(),
        {
            success: vi.fn(),
            warning: vi.fn(),
            info: vi.fn(),
            error: vi.fn(),
            closeAll: vi.fn(),
        },
    ),
}))

describe("KsNotification", () => {
    beforeEach(() => {
        vi.mocked(ElNotification).mockReturnValue({close: vi.fn()} as any)
        vi.mocked(ElNotification.success).mockReturnValue({close: vi.fn()} as any)
        vi.mocked(ElNotification.warning).mockReturnValue({close: vi.fn()} as any)
        vi.mocked(ElNotification.info).mockReturnValue({close: vi.fn()} as any)
        vi.mocked(ElNotification.error).mockReturnValue({close: vi.fn()} as any)
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    test("is callable as a function", () => {
        KsNotification({title: "Done", message: "All tasks finished", type: "success", position: "bottom-right"})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Done",
            message: "All tasks finished",
            type: "success",
            position: "bottom-right",
        })
    })

    test("KsNotification.success delegates to ElNotification.success", () => {
        KsNotification.success({title: "Saved", message: "Flow saved", position: "bottom-right"})
        expect(ElNotification.success).toHaveBeenCalledWith({
            title: "Saved",
            message: "Flow saved",
            position: "bottom-right",
        })
    })

    test("KsNotification.warning delegates to ElNotification.warning", () => {
        KsNotification.warning({title: "Warning", message: "Quota at 85%"})
        expect(ElNotification.warning).toHaveBeenCalledWith({title: "Warning", message: "Quota at 85%"})
    })

    test("KsNotification.info delegates to ElNotification.info", () => {
        KsNotification.info({title: "Info", message: "Scheduled"})
        expect(ElNotification.info).toHaveBeenCalledWith({title: "Info", message: "Scheduled"})
    })

    test("KsNotification.error delegates to ElNotification.error with duration 0", () => {
        KsNotification.error({title: "Error", message: "Task failed", duration: 0})
        expect(ElNotification.error).toHaveBeenCalledWith({
            title: "Error",
            message: "Task failed",
            duration: 0,
        })
    })

    test("KsNotification.closeAll delegates to ElNotification.closeAll", () => {
        KsNotification.closeAll()
        expect(ElNotification.closeAll).toHaveBeenCalled()
    })

    test("returns a handle with a close function", () => {
        const handle = KsNotification({title: "T", message: "M"})
        expect(handle).toHaveProperty("close")
    })
})
