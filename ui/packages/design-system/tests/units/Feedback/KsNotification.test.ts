import {describe, test, expect, vi, beforeEach, afterEach} from "vitest"
import {ElNotification} from "element-plus"
import {KsNotification} from "../../../src/components/Feedback/KsNotification"
import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"
import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
import InformationSlabCircleOutline from "vue-material-design-icons/InformationSlabCircleOutline.vue"

vi.mock("element-plus", () => ({
    ElNotification: Object.assign(vi.fn(), {closeAll: vi.fn()}),
}))

describe("KsNotification", () => {
    beforeEach(() => {
        vi.mocked(ElNotification).mockReturnValue({close: vi.fn()} as any)
    })

    afterEach(() => {
        vi.clearAllMocks()
    })

    // KsNotification strips `type` and re-applies the equivalent class manually, because
    // ElNotification's component ignores `icon` when `type` is set.
    test("strips type and injects MDI success icon + kel-notification--success class", () => {
        KsNotification({title: "Saved", message: "Flow saved", type: "success", position: "bottom-right"})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            title: "Saved",
            message: "Flow saved",
            position: "bottom-right",
            icon: CheckCircleOutline,
            customClass: "kel-notification--success",
        }))
        expect(vi.mocked(ElNotification).mock.calls[0][0]).not.toHaveProperty("type")
    })

    test(".success() applies success MDI icon and class", () => {
        KsNotification.success({title: "T", message: "M"})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            icon: CheckCircleOutline,
            customClass: "kel-notification--success",
        }))
    })

    test(".warning() applies warning MDI icon and class", () => {
        KsNotification.warning({title: "T", message: "M"})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            icon: AlertOutline,
            customClass: "kel-notification--warning",
        }))
    })

    test(".info() applies info MDI icon and class", () => {
        KsNotification.info({title: "T", message: "M"})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            icon: InformationSlabCircleOutline,
            customClass: "kel-notification--info",
        }))
    })

    test(".error() applies error MDI icon and class", () => {
        KsNotification.error({title: "T", message: "M", duration: 0})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            icon: AlertBoxOutline,
            customClass: "kel-notification--error",
            duration: 0,
        }))
    })

    test("merges caller's customClass with the type class", () => {
        KsNotification.error({title: "T", message: "M", customClass: "extra"})
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            customClass: "extra kel-notification--error",
        }))
    })

    test("string input via .success() works", () => {
        KsNotification.success("hi")
        expect(ElNotification).toHaveBeenCalledWith(expect.objectContaining({
            message: "hi",
            icon: CheckCircleOutline,
            customClass: "kel-notification--success",
        }))
    })

    test("bare call without a known type passes options through", () => {
        KsNotification({title: "T", message: "M"})
        expect(ElNotification).toHaveBeenCalledWith({title: "T", message: "M"})
    })

    test("closeAll delegates to ElNotification.closeAll", () => {
        KsNotification.closeAll()
        expect(ElNotification.closeAll).toHaveBeenCalled()
    })

    test("returns a handle with a close function", () => {
        const handle = KsNotification({title: "T", message: "M"})
        expect(handle).toHaveProperty("close")
    })
})
