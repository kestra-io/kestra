import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureAttempts from "./FailureAttempts.vue"
import en from "../../../translations/en.json"

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function attempt(current: string, start: string, end: string, workerId?: string) {
    return {
        state: {current, histories: [{state: "RUNNING", date: start}, {state: current, date: end}]},
        workerId,
    }
}

function mountAttempts(attempts: ReturnType<typeof attempt>[]) {
    return mount(FailureAttempts, {
        props: {
            taskRun: {
                id: "tr-1",
                taskId: "load_warehouse",
                attempts,
                state: {current: "FAILED", histories: []},
            } as never,
        },
        global: {plugins: [i18n], stubs: {KsExecutionStatus: true}},
    })
}

describe("FailureAttempts", () => {
    it("should render one row per attempt", () => {
        const wrapper = mountAttempts([
            attempt("FAILED", "2024-01-01T00:00:00.000Z", "2024-01-01T00:00:02.000Z"),
            attempt("FAILED", "2024-01-01T00:00:10.000Z", "2024-01-01T00:00:12.000Z"),
        ])

        expect(wrapper.findAll(".failure-attempts__row")).toHaveLength(2)
    })

    it("should number attempts from 1 so they match the log view's attempt selector", () => {
        const wrapper = mountAttempts([
            attempt("FAILED", "2024-01-01T00:00:00.000Z", "2024-01-01T00:00:02.000Z"),
            attempt("FAILED", "2024-01-01T00:00:10.000Z", "2024-01-01T00:00:12.000Z"),
        ])

        const labels = wrapper.findAll(".failure-attempts__label").map((node) => node.text())
        expect(labels).toEqual(["Attempt 1", "Attempt 2"])
    })

    it("should show the worker that ran each attempt, so a failure isolated to one worker is visible", () => {
        const wrapper = mountAttempts([
            attempt("FAILED", "2024-01-01T00:00:00.000Z", "2024-01-01T00:00:02.000Z", "worker-a"),
            attempt("FAILED", "2024-01-01T00:00:10.000Z", "2024-01-01T00:00:12.000Z", "worker-b"),
        ])

        const workers = wrapper.findAll(".failure-attempts__worker").map((node) => node.text())
        expect(workers).toEqual(["worker-a", "worker-b"])
    })

    it("should omit the worker when the attempt did not record one", () => {
        const wrapper = mountAttempts([attempt("FAILED", "2024-01-01T00:00:00.000Z", "2024-01-01T00:00:02.000Z")])

        expect(wrapper.find(".failure-attempts__worker").exists()).toBe(false)
    })

    it("should show each attempt's own duration, not the task run's total", () => {
        const wrapper = mountAttempts([
            attempt("FAILED", "2024-01-01T00:00:00.000Z", "2024-01-01T00:00:02.500Z"),
            attempt("FAILED", "2024-01-01T00:00:10.000Z", "2024-01-01T00:00:10.140Z"),
        ])

        const durations = wrapper.findAll(".failure-attempts__row").map((row) => row.findAll(".failure-attempts__meta")[1].text())
        expect(durations).toEqual(["2s, 500ms", "140ms"])
    })

    it("should show an empty state when the task run recorded no attempt", () => {
        const wrapper = mountAttempts([])

        expect(wrapper.find(".failure-attempts__row").exists()).toBe(false)
        expect(wrapper.text()).toContain("No attempt was recorded")
    })
})
