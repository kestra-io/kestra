import {describe, expect, it} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"
import {useSdkDriftBanner} from "../../../src/composables/useSdkDriftBanner"

const SDK_DRIFT_EVENT = "kestra:sdk-drift"

function mountSdkDriftBanner() {
    let api: ReturnType<typeof useSdkDriftBanner>
    const Comp = defineComponent({
        setup() {
            api = useSdkDriftBanner()
            return () => null
        },
    })
    const wrapper = mount(Comp)
    return {api: api!, wrapper}
}

function fireSdkDrift(detail = {label: "@kestra-io/kestra-sdk", committedHash: "aaa", liveHash: "bbb"}) {
    window.dispatchEvent(new CustomEvent(SDK_DRIFT_EVENT, {detail}))
}

describe("useSdkDriftBanner", () => {
    it("has no detail until the drift event fires", () => {
        const {api} = mountSdkDriftBanner()

        expect(api.detail.value).toBeNull()
    })

    it("captures the event detail once the drift event fires", () => {
        const {api} = mountSdkDriftBanner()

        fireSdkDrift()

        expect(api.detail.value).toEqual({label: "@kestra-io/kestra-sdk", committedHash: "aaa", liveHash: "bbb"})
    })

    it("marks itself dismissed once dismiss() is called", () => {
        const {api} = mountSdkDriftBanner()
        fireSdkDrift()

        api.dismiss()

        expect(api.dismissed.value).toBe(true)
    })

    it("stops listening once unmounted", () => {
        const {api, wrapper} = mountSdkDriftBanner()
        wrapper.unmount()

        fireSdkDrift()

        expect(api.detail.value).toBeNull()
    })
})
