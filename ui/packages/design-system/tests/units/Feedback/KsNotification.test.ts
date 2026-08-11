import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {ElNotification} from "element-plus"
import {KsNotification} from "../../../src/components/Feedback/KsNotification"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"

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
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    test("is callable as a function", () => {
        KsNotification({title: "Done", message: "All tasks finished", type: "success", position: "bottom-right"})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Done",
            message: "All tasks finished",
            position: "bottom-right",
            icon: CheckCircleOutline,
            customClass: "kel-notification--success",
        })
    })

    test("KsNotification.success injects success icon via base ElNotification", () => {
        KsNotification.success({title: "Saved", message: "Flow saved", position: "bottom-right"})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Saved",
            message: "Flow saved",
            position: "bottom-right",
            icon: CheckCircleOutline,
            customClass: "kel-notification--success",
        })
    })

    test("KsNotification.warning injects warning icon via base ElNotification", () => {
        KsNotification.warning({title: "Warning", message: "Quota at 85%"})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Warning",
            message: "Quota at 85%",
            icon: AlertCircleOutline,
            customClass: "kel-notification--warning",
        })
    })

    test("KsNotification.info injects info icon via base ElNotification", () => {
        KsNotification.info({title: "Info", message: "Scheduled"})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Info",
            message: "Scheduled",
            icon: InformationOutline,
            customClass: "kel-notification--info",
        })
    })

    test("KsNotification.error injects error icon via base ElNotification with duration 0", () => {
        KsNotification.error({title: "Error", message: "Task failed", duration: 0})
        expect(ElNotification).toHaveBeenCalledWith({
            title: "Error",
            message: "Task failed",
            duration: 0,
            icon: AlertOutline,
            customClass: "kel-notification--error",
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
