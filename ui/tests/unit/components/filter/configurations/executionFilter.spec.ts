import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {createPinia, setActivePinia} from "pinia"
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "executions/list", query: {}}),
}))

import {useExecutionFilter} from "../../../../../src/components/filter/configurations/executionFilter"
import {useFlowExecutionFilter} from "../../../../../src/components/filter/configurations/flowExecutionFilter"
import {useValues} from "../../../../../src/components/filter/composables/useValues"
import {i18nMount} from "../../../i18nMount"

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

// #17947: a standard execution is persisted with a null kind, so an explicit "NORMAL" option
// matched almost nothing while sitting next to the unfiltered option that actually listed them.
// The unfiltered option now carries the Standard wording and NORMAL is gone from the kind list.
describe("execution kind filter offers Standard instead of a broken NORMAL option", () => {
    it.each([
        ["useExecutionFilter", () => useExecutionFilter()],
        ["useFlowExecutionFilter", () => useFlowExecutionFilter()],
    ])("%s wires the Standard wording onto the unfiltered option", (_name, useComposable) => {
        const config = setup(useComposable)
        const kind = config.value.keys.find((k: {key: string}) => k.key === "kind")

        // i18n is not registered in this harness, so t() hands back the key it was given, which is
        // exactly the wiring this config owns; the wording itself lives in KsFilter.locale.ts.
        expect(kind.allLabel).toBe("filter.execution_kind.standard")
        expect(kind.allDescription).toBe("filter.execution_kind.standard_description")
    })

    it("drops NORMAL from the selectable kinds", () => {
        setActivePinia(createPinia())
        let kinds!: {value: string}[]
        const Comp = defineComponent({
            setup() {
                kinds = useValues("executions").VALUES.KINDS
                return () => h("div")
            },
        })
        i18nMount(Comp)

        expect(kinds.map(k => k.value)).not.toContain("NORMAL")
        expect(kinds.map(k => k.value)).toContain("PLAYGROUND")
    })
})
