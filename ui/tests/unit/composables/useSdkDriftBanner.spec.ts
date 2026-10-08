import {describe, it, expect} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"

import {useSdkDriftBanner, type SdkDriftEventDetail} from "../../../src/composables/useSdkDriftBanner"

const DETAIL: SdkDriftEventDetail = {label: "Local UI vs Backend", committedHash: "abc1234", liveHash: "def5678"}

function mountBanner() {
    let result!: ReturnType<typeof useSdkDriftBanner>
    const wrapper = mount(defineComponent({
        setup() {
            result = useSdkDriftBanner()
            return () => null
        },
    }))
    return {wrapper, result}
}

function dispatchDrift() {
    window.dispatchEvent(new CustomEvent("kestra:sdk-drift", {detail: DETAIL}))
}

describe("useSdkDriftBanner", () => {
    it("takes the detail of an SDK drift event", () => {
        const {result} = mountBanner()

        dispatchDrift()

        expect(result.detail.value).toEqual(DETAIL)
    })

    it("stops listening once unmounted", () => {
        const {wrapper, result} = mountBanner()
        wrapper.unmount()

        dispatchDrift()

        expect(result.detail.value).toBeNull()
    })
})
