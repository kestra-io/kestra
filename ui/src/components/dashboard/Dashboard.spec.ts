import {describe, it, expect, vi} from "vitest"
import {defineComponent, h, type PropType} from "vue"
import {flushPromises} from "@vue/test-utils"
import {createPinia} from "pinia"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Chart} from "./composables/useDashboards"

const current = vi.hoisted(() => ({
    route: {name: "flows/update/overview", params: {tenant: "main", namespace: "company.team", id: "parent"}, query: {}} as {name: string; params: Record<string, string>; query: Record<string, string>},
}))

vi.mock("vue-router", async (importOriginal) => {
    const {reactive} = await import("vue")
    current.route = reactive(current.route)
    return {...await importOriginal<typeof import("vue-router")>(), useRoute: () => current.route, useRouter: () => ({push: vi.fn(), replace: vi.fn()})}
})

vi.mock("../../stores/dashboard", async () => {
    const {reactive} = await import("vue")
    const store = reactive({
        activeDashboard: undefined,
        getDashboardId: () => Promise.resolve("default"),
        loadDefaultDefinitions: () => Promise.resolve({
            flow: "id: default\ncharts:\n  - id: description\n    type: io.kestra.plugin.core.dashboard.chart.Markdown\n    source:\n      type: FlowDescription\n      namespace: --NAMESPACE--\n      flowId: --FLOW--\n",
        }),
    })
    return {DEFAULT_DASHBOARD: {id: "default", charts: []}, useDashboardStore: () => store}
})
vi.mock("override/stores/misc", () => ({useMiscStore: () => ({configs: {isCustomDashboardsEnabled: false}})}))
vi.mock("../../composables/useRestoreUrl", () => ({default: () => ({goToRestoreUrl: () => Promise.resolve()})}))
const chartMounts = vi.hoisted(() => ({count: 0}))

vi.mock("./sections/Sections.vue", () => {
    const ChartBlock = defineComponent({
        props: {chart: {type: Object as PropType<Chart>, required: true}},
        setup: (props) => {
            chartMounts.count++
            return () => h("div", props.chart.content)
        },
    })
    return {
        default: defineComponent({
            props: {charts: {type: Array as PropType<Chart[]>, default: () => []}},
            setup: (props) => () => h("div", {"data-test": "sections"}, props.charts.map((chart) => h(ChartBlock, {key: chart.id, chart}))),
        }),
    }
})
vi.mock("@kestra-io/design-system", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/design-system")>(),
    KsFilter: defineComponent({render: () => null}),
}))

import Dashboard from "./Dashboard.vue"

describe("Dashboard", () => {
    it("loads the description of the flow it switches to", async () => {
        const wrapper = i18nMount(Dashboard, {props: {header: false, isFlow: true}, global: {plugins: [createPinia()]}})
        await flushPromises()
        expect(wrapper.get("[data-test=sections]").text()).toContain("flowId: parent")

        current.route.params = {...current.route.params, id: "all_inputs"}
        await flushPromises()

        expect(wrapper.get("[data-test=sections]").text()).toContain("flowId: all_inputs")
        expect(chartMounts.count).toBe(2)
    })
})
