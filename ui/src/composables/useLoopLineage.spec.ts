import {beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"
import {flushPromises, mount} from "@vue/test-utils"
import {useIterationsListInfo} from "./useLoopLineage"

const route = reactive<{query: Record<string, string>}>({query: {}})

const mocks = vi.hoisted(() => ({loadIterationExecution: vi.fn(), resolveLoopLineage: vi.fn()}))

vi.mock("vue-router", () => ({useRoute: () => route}))
vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string, named?: Record<string, unknown>) => named ? `${key}:${Object.values(named).join("/")}` : key}),
}))
vi.mock("./useNamespaceBreadcrumb", async () => {
    const {computed} = await import("vue")
    return {useNamespaceBreadcrumb: () => computed(() => [{label: "executions"}, {label: "company"}])}
})
vi.mock("../utils/loopIterations", () => ({loadIterationExecution: mocks.loadIterationExecution}))
vi.mock("../utils/loopLineage", async (importOriginal) => ({
    ...await importOriginal<typeof import("../utils/loopLineage")>(),
    resolveLoopLineage: mocks.resolveLoopLineage,
}))

const iterationsQuery = {
    "filters[parentId][EQUALS]": "1mubyrnLabcdef",
    "filters[kind][EQUALS]": "LOOP",
    "filters[taskId][EQUALS]": "per_quarter",
}

function mountInfo(enabled = () => true) {
    let info!: ReturnType<typeof useIterationsListInfo>
    mount({template: "<div></div>", setup() {
        info = useIterationsListInfo(enabled)
    }})
    return info
}

describe("useIterationsListInfo", () => {
    beforeEach(() => {
        route.query = {}
        mocks.loadIterationExecution.mockReset().mockResolvedValue({id: "1mubyrnLabcdef", namespace: "company", flowId: "nested_loop_demo"})
        mocks.resolveLoopLineage.mockReset().mockResolvedValue([
            {executionId: "1mubyrnLabcdef", parentExecutionId: "root", taskId: "per_region", number: 1, count: 3, value: "EMEA"},
        ])
    })

    it("names the parent execution the list is filtered to", async () => {
        route.query = iterationsQuery
        const info = mountInfo()
        await flushPromises()

        expect(info.value?.title).toBe("iterations")
        expect(info.value?.breadcrumb.map((crumb) => crumb.label)).toEqual([
            "executions", "company", "nested_loop_demo", "per_region", "1mubyrnL loop-lineage.iteration-count-value:1/3/EMEA", "per_quarter",
        ])
    })

    it("keeps the plain list when the parent cannot be loaded", async () => {
        route.query = iterationsQuery
        mocks.loadIterationExecution.mockRejectedValue(new Error("404"))
        const info = mountInfo()
        await flushPromises()

        expect(info.value).toBeUndefined()
    })

    it("keeps the plain list for an unfiltered list", async () => {
        const info = mountInfo()
        await flushPromises()

        expect(info.value).toBeUndefined()
        expect(mocks.loadIterationExecution).not.toHaveBeenCalled()
    })

    it("keeps the plain list when disabled", async () => {
        route.query = iterationsQuery
        const info = mountInfo(() => false)
        await flushPromises()

        expect(info.value).toBeUndefined()
        expect(mocks.loadIterationExecution).not.toHaveBeenCalled()
    })
})
