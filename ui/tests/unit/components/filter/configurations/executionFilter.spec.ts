import {describe, it, expect, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "executions/list", query: {}}),
}))

import {useExecutionFilter} from "../../../../../src/components/filter/configurations/executionFilter"
import {useFlowExecutionFilter} from "../../../../../src/components/filter/configurations/flowExecutionFilter"

const i18n = createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false, messages: {en: {}}})

function setup<T>(useComposable: () => T): T {
    let api!: T
    const Comp = defineComponent({
        setup() {
            api = useComposable()
            return () => h("div")
        },
    })
    mount(Comp, {global: {plugins: [i18n]}})
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
