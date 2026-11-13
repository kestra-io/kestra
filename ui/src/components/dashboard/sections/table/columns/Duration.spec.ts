import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {durationUtils} from "@kestra-io/design-system"

import Duration from "./Duration.vue"

const START = "2026-10-19T10:00:00.000Z"

describe("Duration", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date(START).getTime() + 10_000)
    })

    afterEach(() => vi.useRealTimers())

    it("renders a numeric field through humanDuration inside a span", () => {
        const wrapper = mount(Duration, {props: {field: 125, startDate: START}})

        expect(wrapper.find("span").text()).toBe(durationUtils.humanDuration(125))
        expect(wrapper.find("em").exists()).toBe(false)
    })

    it("renders the live elapsed seconds inside an em when field is missing", () => {
        const wrapper = mount(Duration, {props: {field: undefined, startDate: START}})

        expect(wrapper.find("em").text()).toBe(durationUtils.humanDuration(10))
        expect(wrapper.find("span").exists()).toBe(false)
    })

    it("falls back to 0 when neither field nor startDate is set", () => {
        const wrapper = mount(Duration, {props: {field: undefined}})

        expect(wrapper.find("em").text()).toBe(durationUtils.humanDuration(0))
        expect(wrapper.find("span").exists()).toBe(false)
    })

    it("treats a field of 0 as a real duration rather than a live elapsed time", () => {
        const wrapper = mount(Duration, {props: {field: 0, startDate: START}})

        // v-if="field" is falsy for 0, so the real zero still renders in the em branch.
        expect(wrapper.find("em").text()).toBe(durationUtils.humanDuration(0))
        expect(wrapper.find("span").exists()).toBe(false)
    })

    it("keeps counting a running execution when a refresh re-renders it with the same props", async () => {
        const wrapper = mount(Duration, {props: {field: undefined, startDate: START}})
        expect(wrapper.text()).toBe(durationUtils.humanDuration(10))

        await vi.advanceTimersByTimeAsync(60_000)
        await wrapper.setProps({field: undefined, startDate: START})

        expect(wrapper.text()).toBe(durationUtils.humanDuration(70))
    })

    it("shows the duration sent by the backend once the execution reaches a terminal state", async () => {
        const wrapper = mount(Duration, {props: {field: undefined, startDate: START}})

        await vi.advanceTimersByTimeAsync(60_000)
        await wrapper.setProps({field: 65, startDate: START})

        await vi.advanceTimersByTimeAsync(5_000)
        expect(wrapper.text()).toBe(durationUtils.humanDuration(65))
    })
})
