import {afterEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import type {Execution} from "../stores/executions"
import {useExecutionSummary} from "./useExecutionSummary"

const {copy, success} = vi.hoisted(() => ({copy: vi.fn(), success: vi.fn()}))

vi.mock("../utils/utils", () => ({copy}))
vi.mock("../utils/toast", () => ({useToast: () => ({success})}))

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {copied: "Copied"}}})

function mountSummary(execution: Partial<Execution>) {
    let summary!: ReturnType<typeof useExecutionSummary>
    const wrapper = mount(defineComponent({
        setup() {
            summary = useExecutionSummary(() => execution as Execution)
            return () => h("div")
        },
    }), {global: {plugins: [i18n]}})

    return {summary, wrapper}
}

describe("useExecutionSummary", () => {
    let wrapper: VueWrapper | undefined

    afterEach(() => {
        wrapper?.unmount()
        wrapper = undefined
        vi.clearAllMocks()
    })

    it("capitalises the current status", () => {
        const mounted = mountSummary({id: "execution-id", state: {current: "SUCCESS"} as Execution["state"]})
        wrapper = mounted.wrapper

        expect(mounted.summary.statusLabel.value).toBe("Success")
    })

    it("recognises replay and replayed labels", () => {
        const replay = mountSummary({labels: [{key: "system.replay", value: "true"}]} as Partial<Execution>)
        wrapper = replay.wrapper
        expect(replay.summary.replayed.value).toBe(true)
        wrapper.unmount()

        const replayed = mountSummary({labels: [{key: "system.replayed", value: "true"}]} as Partial<Execution>)
        wrapper = replayed.wrapper
        expect(replayed.summary.replayed.value).toBe(true)
    })

    it("recognises restarted labels only as restarted", () => {
        const mounted = mountSummary({labels: [{key: "system.restarted", value: "true"}]})
        wrapper = mounted.wrapper

        expect(mounted.summary.restarted.value).toBe(true)
        expect(mounted.summary.replayed.value).toBe(false)
    })

    it("requires a true string label value and handles missing labels", () => {
        const mounted = mountSummary({labels: [{key: "system.replay", value: true}, {key: "system.restarted", value: "false"}]} as unknown as Partial<Execution>)
        wrapper = mounted.wrapper
        expect(mounted.summary.replayed.value).toBe(false)
        expect(mounted.summary.restarted.value).toBe(false)
        wrapper.unmount()

        const withoutLabels = mountSummary({})
        wrapper = withoutLabels.wrapper
        expect(withoutLabels.summary.replayed.value).toBe(false)
        expect(withoutLabels.summary.restarted.value).toBe(false)
    })

    it("uses the first state history date", () => {
        const date = "2026-10-01T12:00:00Z"
        const mounted = mountSummary({state: {histories: [{date}]} as Execution["state"]})
        wrapper = mounted.wrapper

        expect(mounted.summary.createdDate.value).toEqual(new Date(date))
    })

    it("filters replay links by parent id or falls back to the execution id", () => {
        const replay = mountSummary({id: "replay-id", parentId: "parent-id", labels: [{key: "system.replay", value: "true"}]})
        wrapper = replay.wrapper
        expect(replay.summary.replaysLink.value.query["filters[parentId][EQUALS]"]).toBe("parent-id")
        wrapper.unmount()

        const replayWithoutParent = mountSummary({id: "replay-id", labels: [{key: "system.replay", value: "true"}]})
        wrapper = replayWithoutParent.wrapper
        expect(replayWithoutParent.summary.replaysLink.value.query["filters[parentId][EQUALS]"]).toBe("replay-id")
    })

    it("filters non-replay links by the execution id", () => {
        const mounted = mountSummary({id: "execution-id"})
        wrapper = mounted.wrapper

        expect(mounted.summary.replaysLink.value).toEqual({
            name: "executions/list",
            query: {"filters[parentId][EQUALS]": "execution-id", sort: "state.endDate:desc"},
        })
    })

    it("copies the execution id and shows a success toast", () => {
        const mounted = mountSummary({id: "execution-id"})
        wrapper = mounted.wrapper

        mounted.summary.copyId()

        expect(copy).toHaveBeenCalledWith("execution-id")
        expect(success).toHaveBeenCalledWith("Copied")
    })
})
