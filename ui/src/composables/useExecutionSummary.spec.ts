import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {i18nMount} from "../../tests/unit/i18nMount"
import type {Execution} from "../stores/executions"
import {useExecutionSummary} from "./useExecutionSummary"

const {copy, success} = vi.hoisted(() => ({copy: vi.fn(), success: vi.fn()}))

vi.mock("../utils/utils", () => ({copy}))
vi.mock("../utils/toast", () => ({useToast: () => ({success})}))

function mountSummary(execution: Partial<Execution>) {
    let summary!: ReturnType<typeof useExecutionSummary>
    const host = defineComponent({
        setup() {
            summary = useExecutionSummary(() => execution as Execution)
            return () => h("div")
        },
    })
    i18nMount(host, {messages: {copied: "Copied"}})

    return summary
}

describe("useExecutionSummary", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("capitalises the current status", () => {
        const summary = mountSummary({id: "execution-id", state: {current: "SUCCESS"} as Execution["state"]})

        expect(summary.statusLabel.value).toBe("Success")
    })

    it.each(["system.replay", "system.replayed"])("treats %s as replayed", (key) => {
        const summary = mountSummary({labels: [{key, value: "true"}]})

        expect(summary.replayed.value).toBe(true)
    })

    it("treats system.restarted as restarted only", () => {
        const summary = mountSummary({labels: [{key: "system.restarted", value: "true"}]})

        expect(summary.restarted.value).toBe(true)
        expect(summary.replayed.value).toBe(false)
    })

    it("ignores labels whose value is not \"true\"", () => {
        const summary = mountSummary({labels: [{key: "system.replay", value: "false"}, {key: "system.restarted", value: "false"}]})

        expect(summary.replayed.value).toBe(false)
        expect(summary.restarted.value).toBe(false)
    })

    it("is neither replayed nor restarted without labels", () => {
        const summary = mountSummary({})

        expect(summary.replayed.value).toBe(false)
        expect(summary.restarted.value).toBe(false)
    })

    it("uses the first state history date", () => {
        const date = "2026-10-01T12:00:00Z"
        const summary = mountSummary({state: {histories: [{date}, {date: "2026-10-01T13:00:00Z"}]} as Execution["state"]})

        expect(summary.createdDate.value).toEqual(new Date(date))
    })

    it("filters replay links by the parent id", () => {
        const summary = mountSummary({id: "replay-id", parentId: "parent-id", labels: [{key: "system.replay", value: "true"}]})

        expect(summary.replaysLink.value.query["filters[parentId][EQUALS]"]).toBe("parent-id")
    })

    it("falls back to the execution id when a replay has no parent id", () => {
        const summary = mountSummary({id: "replay-id", labels: [{key: "system.replay", value: "true"}]})

        expect(summary.replaysLink.value.query["filters[parentId][EQUALS]"]).toBe("replay-id")
    })

    it("filters non-replay links by the execution id", () => {
        const summary = mountSummary({id: "execution-id", parentId: "parent-id"})

        expect(summary.replaysLink.value).toEqual({
            name: "executions/list",
            query: {"filters[parentId][EQUALS]": "execution-id", sort: "state.endDate:desc"},
        })
    })

    it("copies the execution id and shows a success toast", () => {
        const summary = mountSummary({id: "execution-id"})

        summary.copyId()

        expect(copy).toHaveBeenCalledWith("execution-id")
        expect(success).toHaveBeenCalledWith("Copied")
    })
})
