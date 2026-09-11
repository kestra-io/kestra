import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureStateHistory from "./FailureStateHistory.vue"
import en from "../../../translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function history(state: string, date: string) {
    return {state, date}
}

function mountHistory(histories: ReturnType<typeof history>[]) {
    return mount(FailureStateHistory, {
        props: {
            taskRun: {
                id: "tr-1",
                taskId: "load_warehouse",
                state: {current: "FAILED", histories},
            } as never,
        },
        global: {plugins: [i18n]},
    })
}

describe("FailureStateHistory", () => {
    it("should render one row per state transition, earliest first", () => {
        const wrapper = mountHistory([
            history("CREATED", "2024-01-01T00:00:00.000Z"),
            history("RUNNING", "2024-01-01T00:00:00.050Z"),
            history("FAILED", "2024-01-01T00:00:00.190Z"),
        ])

        expect(wrapper.findAll(".state-history-row")).toHaveLength(3)
    })

    it("should not show an elapsed duration on the first transition", () => {
        const wrapper = mountHistory([
            history("CREATED", "2024-01-01T00:00:00.000Z"),
            history("RUNNING", "2024-01-01T00:00:00.050Z"),
        ])

        const rows = wrapper.findAll(".state-history-row")
        expect(rows[0].find(".state-history-row__elapsed").exists()).toBe(false)
    })

    it("should show millisecond-level elapsed durations rather than rounding fast transitions to 0s", () => {
        const wrapper = mountHistory([
            history("CREATED", "2024-01-01T00:00:00.131Z"),
            history("SUBMITTED", "2024-01-01T00:00:00.134Z"),
            history("RUNNING", "2024-01-01T00:00:00.138Z"),
        ])

        const rows = wrapper.findAll(".state-history-row")
        expect(rows[1].get(".state-history-row__elapsed").text()).toBe("+3ms")
        expect(rows[2].get(".state-history-row__elapsed").text()).toBe("+4ms")
    })

    it("should show the elapsed duration since the previous transition", () => {
        const wrapper = mountHistory([
            history("CREATED", "2024-01-01T00:00:00.000Z"),
            history("RUNNING", "2024-01-01T00:00:00.050Z"),
            history("FAILED", "2024-01-01T00:00:00.190Z"),
        ])

        const rows = wrapper.findAll(".state-history-row")
        expect(rows[1].get(".state-history-row__elapsed").text()).toBe("+50ms")
        expect(rows[2].get(".state-history-row__elapsed").text()).toBe("+140ms")
    })
})
