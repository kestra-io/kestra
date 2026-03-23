import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import * as ElementPlus from "element-plus"
import {KsNotification} from "../../../src/components/Feedback/KsNotification"

describe("KsNotification", () => {
    beforeEach(() => {
        vi.spyOn(ElementPlus, "ElNotification").mockReturnValue({close: vi.fn()} as any)
        ;(ElementPlus.ElNotification as any).success = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElNotification as any).warning = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElNotification as any).info = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElNotification as any).error = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElNotification as any).closeAll = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("is callable as a function", () => {
        KsNotification({title: "Done", message: "All tasks finished", type: "success", position: "bottom-right"})
        expect(ElementPlus.ElNotification).toHaveBeenCalledWith({
            title: "Done",
            message: "All tasks finished",
            type: "success",
            position: "bottom-right",
        })
    })

    test("KsNotification.success delegates to ElNotification.success", () => {
        KsNotification.success({title: "Saved", message: "Flow saved", position: "bottom-right"})
        expect(ElementPlus.ElNotification.success).toHaveBeenCalledWith({
            title: "Saved",
            message: "Flow saved",
            position: "bottom-right",
        })
    })

    test("KsNotification.warning delegates to ElNotification.warning", () => {
        KsNotification.warning({title: "Warning", message: "Quota at 85%"})
        expect(ElementPlus.ElNotification.warning).toHaveBeenCalledWith({title: "Warning", message: "Quota at 85%"})
    })

    test("KsNotification.info delegates to ElNotification.info", () => {
        KsNotification.info({title: "Info", message: "Scheduled"})
        expect(ElementPlus.ElNotification.info).toHaveBeenCalledWith({title: "Info", message: "Scheduled"})
    })

    test("KsNotification.error delegates to ElNotification.error with duration 0", () => {
        KsNotification.error({title: "Error", message: "Task failed", duration: 0})
        expect(ElementPlus.ElNotification.error).toHaveBeenCalledWith({
            title: "Error",
            message: "Task failed",
            duration: 0,
        })
    })

    test("KsNotification.closeAll delegates to ElNotification.closeAll", () => {
        KsNotification.closeAll()
        expect(ElementPlus.ElNotification.closeAll).toHaveBeenCalled()
    })

    test("returns a handle with a close function", () => {
        const handle = KsNotification({title: "T", message: "M"})
        expect(handle).toHaveProperty("close")
    })
})
