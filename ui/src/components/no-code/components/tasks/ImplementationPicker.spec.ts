import {describe, expect, it, vi} from "vitest"
import {i18nMount} from "../../../../../tests/unit/i18nMount"
import ImplementationPicker from "./ImplementationPicker.vue"
import {getImplementationBranches} from "./discriminatedUnion"
import type {Schema} from "./getTaskComponent"

vi.mock("../../../../stores/plugins", () => ({
    usePluginsStore: () => ({icons: {}, loadIcon: vi.fn()}),
}))

const KPI = "io.kestra.plugin.core.dashboard.chart.KPI"
const executionsKey = `${KPI}_io.kestra.plugin.core.dashboard.data.IExecutions-Fields___`
const flowsKey = `${KPI}_io.kestra.plugin.core.dashboard.data.IFlows-Fields___`

const definitions: Record<string, Schema> = {
    [executionsKey]: {type: "object", properties: {type: {const: KPI}, data: {$ref: "#/definitions/io.kestra.plugin.core.dashboard.data.ExecutionsKPI"}}},
    [flowsKey]: {type: "object", properties: {type: {const: KPI}, data: {$ref: "#/definitions/io.kestra.plugin.core.dashboard.data.FlowsKPI"}}},
    "io.kestra.plugin.core.dashboard.data.ExecutionsKPI": {type: "object", properties: {type: {const: "io.kestra.plugin.core.dashboard.data.ExecutionsKPI"}}},
    "io.kestra.plugin.core.dashboard.data.FlowsKPI": {type: "object", properties: {type: {const: "io.kestra.plugin.core.dashboard.data.FlowsKPI"}}},
}

const branches = getImplementationBranches({anyOf: [{$ref: `#/definitions/${executionsKey}`}, {$ref: `#/definitions/${flowsKey}`}]}, definitions) ?? []

describe("ImplementationPicker", () => {
    it("marks only the branch the value holds when several branches share a type", () => {
        const wrapper = i18nMount(ImplementationPicker, {
            props: {title: "Choose", branches, current: flowsKey, modelValue: true},
            global: {stubs: {KsDialog: {template: "<div><slot /></div>"}, TaskIcon: true}},
        })

        const options = wrapper.findAll("[data-test=plugin-implementation-picker-option]")
        const checked = options.filter((option) => option.attributes("aria-checked") === "true")

        expect(options).toHaveLength(2)
        expect(checked).toHaveLength(1)
        expect(checked[0].text()).toContain("Flows KPI")
    })
})
