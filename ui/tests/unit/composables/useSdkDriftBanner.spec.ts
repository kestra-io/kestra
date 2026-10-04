import {describe, it, expect} from "vitest"
import {defineComponent} from "vue"
import {mount} from "@vue/test-utils"

import {useSdkDriftBanner, type SdkDriftEventDetail} from "../../../src/composables/useSdkDriftBanner"

const DRIFT_EVENT_NAME = "kestra:sdk-drift"

const MOCK_DRIFT_PAYLOAD: SdkDriftEventDetail = {
    label: "Local UI vs Backend",
    committedHash: "abc1234",
    liveHash: "def5678",
}

function mountSdkDriftBanner() {
    let result!: ReturnType<typeof useSdkDriftBanner>

    const wrapper = mount(
        defineComponent({
            setup() {
                result = useSdkDriftBanner()
                return () => null
            },
        }),
    )

    return {wrapper, result}
}

function triggerDriftEvent(detail: SdkDriftEventDetail) {
    window.dispatchEvent(new CustomEvent(DRIFT_EVENT_NAME, {detail}))
}

describe("useSdkDriftBanner", () => {
    it("starts with no detail and is not dismissed", () => {
        const {wrapper, result} = mountSdkDriftBanner()

        expect(result.detail.value).toBeNull()
        expect(result.dismissed.value).toBe(false)

        wrapper.unmount()
    })

    it("updates detail when an SDK drift event is dispatched", () => {
        const {wrapper, result} = mountSdkDriftBanner()

        triggerDriftEvent(MOCK_DRIFT_PAYLOAD)

        expect(result.detail.value).toEqual(MOCK_DRIFT_PAYLOAD)

        wrapper.unmount()
    })

    it("replaces the previous detail when another event is dispatched", () => {
        const {wrapper, result} = mountSdkDriftBanner()

        const updatedPayload: SdkDriftEventDetail = {
            ...MOCK_DRIFT_PAYLOAD,
            committedHash: "new999",
        }

        triggerDriftEvent(MOCK_DRIFT_PAYLOAD)
        expect(result.detail.value).toEqual(MOCK_DRIFT_PAYLOAD)

        triggerDriftEvent(updatedPayload)

        expect(result.detail.value).toEqual(updatedPayload)

        wrapper.unmount()
    })

    it("sets dismissed to true without changing detail", () => {
        const {wrapper, result} = mountSdkDriftBanner()

        triggerDriftEvent(MOCK_DRIFT_PAYLOAD)
        result.dismiss()

        expect(result.dismissed.value).toBe(true)
        expect(result.detail.value).toEqual(MOCK_DRIFT_PAYLOAD)

        wrapper.unmount()
    })

    it("ignores drift events after component unmount", () => {
        const {wrapper, result} = mountSdkDriftBanner()

        triggerDriftEvent(MOCK_DRIFT_PAYLOAD)
        expect(result.detail.value).toEqual(MOCK_DRIFT_PAYLOAD)

        wrapper.unmount()

        const laterPayload: SdkDriftEventDetail = {
            ...MOCK_DRIFT_PAYLOAD,
            committedHash: "new999",
        }

        triggerDriftEvent(laterPayload)

        expect(result.detail.value).toEqual(MOCK_DRIFT_PAYLOAD)
    })
})