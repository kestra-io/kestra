import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import * as ElementPlus from "element-plus"
import {KsMessage} from "../../../src/components/Feedback/KsMessage"

describe("KsMessage", () => {
    beforeEach(() => {
        vi.spyOn(ElementPlus, "ElMessage").mockReturnValue({close: vi.fn()} as any)
        ;(ElementPlus.ElMessage as any).success = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElMessage as any).warning = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElMessage as any).info = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElMessage as any).error = vi.fn().mockReturnValue({close: vi.fn()})
        ;(ElementPlus.ElMessage as any).closeAll = vi.fn()
    })

    afterEach(() => {
        vi.restoreAllMocks()
    })

    test("is callable as a function", () => {
        KsMessage({message: "test", type: "info"})
        expect(ElementPlus.ElMessage).toHaveBeenCalledWith({message: "test", type: "info"})
    })

    test("KsMessage.success delegates to ElMessage.success", () => {
        KsMessage.success("saved")
        expect(ElementPlus.ElMessage.success).toHaveBeenCalledWith("saved")
    })

    test("KsMessage.warning delegates to ElMessage.warning", () => {
        KsMessage.warning("check this")
        expect(ElementPlus.ElMessage.warning).toHaveBeenCalledWith("check this")
    })

    test("KsMessage.info delegates to ElMessage.info", () => {
        KsMessage.info({message: "fyi"})
        expect(ElementPlus.ElMessage.info).toHaveBeenCalledWith({message: "fyi"})
    })

    test("KsMessage.error delegates to ElMessage.error", () => {
        KsMessage.error("something broke")
        expect(ElementPlus.ElMessage.error).toHaveBeenCalledWith("something broke")
    })

    test("KsMessage.closeAll delegates to ElMessage.closeAll", () => {
        KsMessage.closeAll()
        expect(ElementPlus.ElMessage.closeAll).toHaveBeenCalled()
    })

    test("KsMessage.closeAll passes type argument", () => {
        KsMessage.closeAll("error")
        expect(ElementPlus.ElMessage.closeAll).toHaveBeenCalledWith("error")
    })

    test("returns a MessageHandler with a close function", () => {
        const handler = KsMessage({message: "hello"})
        expect(handler).toHaveProperty("close")
    })
})
