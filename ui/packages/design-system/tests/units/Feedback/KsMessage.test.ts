import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {ElMessage} from "element-plus"
import {KsMessage} from "../../../src/components/Feedback/KsMessage"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"

vi.mock("element-plus", () => ({
    ElMessage: Object.assign(
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

describe("KsMessage", () => {
    beforeEach(() => {
        vi.mocked(ElMessage).mockReturnValue({close: vi.fn()} as any)
        vi.mocked(ElMessage.success).mockReturnValue({close: vi.fn()})
        vi.mocked(ElMessage.warning).mockReturnValue({close: vi.fn()})
        vi.mocked(ElMessage.info).mockReturnValue({close: vi.fn()})
        vi.mocked(ElMessage.error).mockReturnValue({close: vi.fn()})
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    test("is callable as a function", () => {
        KsMessage({message: "test", type: "info"})
        expect(ElMessage).toHaveBeenCalledWith({
            message: "test",
            type: "info",
            plain: true,
            icon: InformationOutline,
        })
    })

    test("KsMessage.success delegates to ElMessage.success", () => {
        KsMessage.success("saved")
        expect(ElMessage.success).toHaveBeenCalledWith({
            message: "saved",
            plain: true,
            icon: CheckCircleOutline,
        })
    })

    test("KsMessage.warning delegates to ElMessage.warning", () => {
        KsMessage.warning("check this")
        expect(ElMessage.warning).toHaveBeenCalledWith({
            message: "check this",
            plain: true,
            icon: AlertCircleOutline,
        })
    })

    test("KsMessage.info delegates to ElMessage.info", () => {
        KsMessage.info({message: "fyi"})
        expect(ElMessage.info).toHaveBeenCalledWith({
            message: "fyi",
            plain: true,
            icon: InformationOutline,
        })
    })

    test("KsMessage.error delegates to ElMessage.error", () => {
        KsMessage.error("something broke")
        expect(ElMessage.error).toHaveBeenCalledWith({
            message: "something broke",
            plain: true,
            icon: AlertOutline,
        })
    })

    test("KsMessage.closeAll delegates to ElMessage.closeAll", () => {
        KsMessage.closeAll()
        expect(ElMessage.closeAll).toHaveBeenCalled()
    })

    test("KsMessage.closeAll passes type argument", () => {
        KsMessage.closeAll("error")
        expect(ElMessage.closeAll).toHaveBeenCalledWith("error")
    })

    test("returns a MessageHandler with a close function", () => {
        const handler = KsMessage({message: "hello"})
        expect(handler).toHaveProperty("close")
    })
})
