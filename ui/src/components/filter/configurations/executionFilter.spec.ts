import {beforeEach, describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {createPinia, setActivePinia} from "pinia"
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "executions/list", query: {}}),
}))

import {Comparators} from "@kestra-io/design-system"
import {useExecutionFilter} from "./executionFilter"
import {useFlowExecutionFilter} from "./flowExecutionFilter"
import {useLogFilter} from "./logFilter"
import {useLogExecutionsFilter} from "./logExecutionsFilter"
import {i18nMount} from "../../../../tests/unit/i18nMount"

function setup<T>(useComposable: () => T): T {
    let api!: T
    const Comp = defineComponent({
        setup() {
            api = useComposable()
            return () => h("div")
        },
    })
    i18nMount(Comp)
    return api
}

describe("execution kind choices", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it.each([
        {name: "executions", useFilter: useExecutionFilter},
        {name: "flow executions", useFilter: useFlowExecutionFilter},
    ])("combines normal executions with the default option in $name", async ({useFilter}) => {
        const {kind, options} = setup(() => {
            const key = useFilter().value.keys.find(candidate => candidate.key === "kind")!
            return {kind: key, options: key.valueProvider!()}
        })

        expect(kind.allLabel).toBe("filter.execution_kind.standard")
        expect(kind.allDescription).toBe("filter.execution_kind.standard_description")
        const values = (await options).map(option => option.value)
        expect(values).toContain("PLAYGROUND")
        expect(values).not.toContain("NORMAL")
    })

    it.each([
        {name: "logs", useFilter: useLogFilter},
        {name: "execution logs", useFilter: () => useLogExecutionsFilter(false, "PLAYGROUND")},
    ])("preserves normal in the logs multi-select in $name", async ({useFilter}) => {
        const options = await setup(() => useFilter().value.keys.find(key => key.key === "kind")!.valueProvider!())

        expect(options.map(option => option.value)).toEqual(expect.arrayContaining(["NORMAL", "PLAYGROUND", "LOOP"]))
    })
})

// Regression #18438: the Gantt/Logs "iterations" link scopes the executions list with
// filters[parentId]/[kind]/[taskId], but `taskId` was never declared here, so
// keepSupportedFilters (ui/src/components/executions/utils.ts) silently dropped it before
// the search request ever reached the API.
describe("execution filter configurations declare taskId", () => {
    it("useExecutionFilter", () => {
        const config = setup(() => useExecutionFilter())
        expect(config.value.keys.map((k: {key: string}) => k.key)).toContain("taskId")
    })

    it("useFlowExecutionFilter", () => {
        const config = setup(() => useFlowExecutionFilter())
        expect(config.value.keys.map((k: {key: string}) => k.key)).toContain("taskId")
    })
})

// Issue #12784: the Trigger tab links to the executions list pre-filtered on the
// trigger id, but `triggerId` was never declared here, so keepSupportedFilters would
// silently drop filters[triggerId][EQUALS] before the search request reached the API.
describe("execution filter configurations declare triggerId", () => {
    it("useExecutionFilter", () => {
        const config = setup(() => useExecutionFilter())
        expect(config.value.keys.map((k: {key: string}) => k.key)).toContain("triggerId")
    })

    it("useFlowExecutionFilter", () => {
        const config = setup(() => useFlowExecutionFilter())
        expect(config.value.keys.map((k: {key: string}) => k.key)).toContain("triggerId")
    })
})

describe("execution filter configurations declare searchable outputs", () => {
    it.each([
        ["useExecutionFilter", useExecutionFilter],
        ["useFlowExecutionFilter", useFlowExecutionFilter],
    ])("%s", (_name, useComposable) => {
        const config = setup(() => useComposable())
        const outputs = config.value.keys.find((k: {key: string}) => k.key === "outputs")
        expect(outputs).toMatchObject({valueType: "key-value", comparators: [Comparators.EQUALS, Comparators.NOT_EQUALS, Comparators.IN, Comparators.NOT_IN]})
    })
})
