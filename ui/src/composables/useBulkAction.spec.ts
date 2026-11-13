import {beforeEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h, ref} from "vue"
import {i18nMount} from "../../tests/unit/i18nMount"
import {useBulkAction} from "./useBulkAction"

const {success, error} = vi.hoisted(() => ({success: vi.fn(), error: vi.fn()}))

vi.mock("../utils/toast", () => ({useToast: () => ({success, error})}))

const BULK_REFUSED = {
    type: "https://kestra.io/problems/bulk-validation-failed",
    title: "Bulk validation failed",
    status: 400,
    detail: "One or more flows could not be deleted.",
    errors: [
        {detail: "flow not found", path: "qa.flow_a"},
        {detail: "flow not found", path: "qa.flow_b"},
    ],
}

function httpError(status: number, problem: object) {
    return Object.assign(new Error(`Request failed with ${status}`), {status, problem, response: {data: problem, status}})
}

function mountBulkAction(queryBulkAction: boolean, options: Parameters<typeof useBulkAction>[1] = {}) {
    const table = ref({queryBulkAction, toggleAllUnselected: vi.fn(), reload: vi.fn()})
    let bulk!: ReturnType<typeof useBulkAction>
    i18nMount(defineComponent({
        setup() {
            bulk = useBulkAction(table, options)
            return () => h("div")
        },
    }))
    return {table: table.value, runBulkAction: bulk.runBulkAction}
}

describe("useBulkAction", () => {
    beforeEach(() => {
        vi.clearAllMocks()
    })

    it.each([
        ["every match", true, "byQuery"],
        ["the ticked rows", false, "byIds"],
    ])("acts on %s, then reports, unselects and refreshes", async (_label, queryBulkAction, expectedCall) => {
        const {table, runBulkAction} = mountBulkAction(queryBulkAction)
        const byQuery = vi.fn<() => Promise<{count: number}>>().mockResolvedValue({count: 3})
        const byIds = vi.fn<() => Promise<{count: number}>>().mockResolvedValue({count: 2})

        await runBulkAction({byQuery, byIds, success: (r) => `${r.count} flows deleted`})

        expect((expectedCall === "byQuery" ? byQuery : byIds)).toHaveBeenCalledOnce()
        expect((expectedCall === "byQuery" ? byIds : byQuery)).not.toHaveBeenCalled()
        expect(success).toHaveBeenCalledWith(expectedCall === "byQuery" ? "3 flows deleted" : "2 flows deleted")
        expect(table.toggleAllUnselected).toHaveBeenCalledOnce()
        expect(table.reload).toHaveBeenCalledOnce()
    })

    it("leaves the outcome to the page's own report when one is given", async () => {
        const {table, runBulkAction} = mountBulkAction(false)
        const report = vi.fn()
        const done = () => Promise.resolve({count: 0})

        await runBulkAction({byQuery: done, byIds: done, report})

        expect(report).toHaveBeenCalledWith({count: 0})
        expect(success).not.toHaveBeenCalled()
        expect(table.toggleAllUnselected).toHaveBeenCalledOnce()
        expect(table.reload).toHaveBeenCalledOnce()
    })

    it("refreshes with the page's own refresh instead of reloading the table when one is given", async () => {
        const refresh = vi.fn()
        const {table, runBulkAction} = mountBulkAction(false, {refresh})
        const done = () => Promise.resolve({count: 1})

        await runBulkAction({byQuery: done, byIds: done, success: () => "done"})

        expect(refresh).toHaveBeenCalledOnce()
        expect(table.reload).not.toHaveBeenCalled()
    })

    it.each([true, false])("lists each refused item and keeps the selection when the server refuses the batch (every match: %s)", async (queryBulkAction) => {
        const {table, runBulkAction} = mountBulkAction(queryBulkAction)
        const refused = () => Promise.reject(httpError(400, BULK_REFUSED))

        await runBulkAction({byQuery: refused, byIds: refused, success: () => "done"})

        expect(error).toHaveBeenCalledWith([{message: "flow not found"}, {message: "flow not found"}], "Bulk validation failed")
        expect(success).not.toHaveBeenCalled()
        expect(table.toggleAllUnselected).not.toHaveBeenCalled()
        expect(table.reload).not.toHaveBeenCalled()
    })

    it("adds no second toast for a failure the global error toast already reports", async () => {
        const {table, runBulkAction} = mountBulkAction(false)
        const failed = () => Promise.reject(httpError(500, {type: "about:blank", title: "Internal Server Error", status: 500}))

        await runBulkAction({byQuery: failed, byIds: failed, success: () => "done"})

        expect(error).not.toHaveBeenCalled()
        expect(success).not.toHaveBeenCalled()
        expect(table.toggleAllUnselected).not.toHaveBeenCalled()
        expect(table.reload).not.toHaveBeenCalled()
    })
})
