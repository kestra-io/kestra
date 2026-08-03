import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {i18n} from "./_helpers"
import CopilotContextChip from "../../../../../src/components/ai/copilot/CopilotContextChip.vue"

// KsTag stub with a working close button so we can assert the remove emit; `data-test`/listeners
// fall through to the root.
const KsTag = {
    name: "KsTag",
    props: {closable: Boolean, icon: {type: [Object, Function], default: undefined}, size: {type: String, default: undefined}},
    emits: ["close"],
    template: "<span class=\"ks-tag\"><slot /><button v-if=\"closable\" class=\"close\" @click=\"$emit('close')\">x</button></span>",
}
const KsIcon = {name: "KsIcon", template: "<i><slot /></i>"}
// KsId renders the value as a code-styled token; stub it to expose the value + the <code> wrapper.
const KsId = {name: "KsId", props: {value: {type: String, default: ""}, shrink: {type: Boolean, default: true}}, template: "<code class=\"ks-id\">{{ value }}</code>"}

const mountChip = (scope: any) =>
    mount(CopilotContextChip, {props: {scope}, global: {plugins: [i18n], stubs: {KsTag, KsIcon, KsId}}})

const ids = (w: ReturnType<typeof mountChip>) => w.findAll("code.ks-id").map((c) => c.text())

describe("CopilotContextChip", () => {
    it("renders a flow scope as a Flow pill + a Namespace pill, values as code tokens", () => {
        const w = mountChip({kind: "FLOW", namespace: "company.team", flowId: "my-flow"})
        expect(w.text()).toContain("Flow:")
        expect(w.text()).toContain("Namespace:")
        expect(w.find("[data-test=\"copilot-context-flowId\"]").exists()).toBe(true)
        // The resource comes first, its namespace second — each value a KsId code token.
        expect(ids(w)).toEqual(["my-flow", "company.team"])
    })

    it("renders an execution scope as an Execution pill + a Namespace pill", () => {
        const w = mountChip({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"})
        expect(w.text()).toContain("Execution:")
        expect(ids(w)).toEqual(["exec-1", "company.team"])
    })

    it("renders a namespace scope as a single Namespace pill", () => {
        const w = mountChip({kind: "NAMESPACE", namespace: "company.team"})
        expect(w.text()).toContain("Namespace:")
        expect(ids(w)).toEqual(["company.team"])
    })

    it("renders id-only resources (dashboard / app / blueprint / plugin) as a single code-token pill", () => {
        expect(ids(mountChip({kind: "DASHBOARD", dashboardId: "my-dash"}))).toEqual(["my-dash"])
        expect(ids(mountChip({kind: "APP", appId: "my-app"}))).toEqual(["my-app"])
        expect(ids(mountChip({kind: "BLUEPRINT", blueprintId: "bp-1"}))).toEqual(["bp-1"])
        const plugin = mountChip({kind: "PLUGIN", pluginId: "io.kestra.plugin.core.log.Log"})
        expect(plugin.text()).toContain("Plugin:")
        expect(ids(plugin)).toEqual(["io.kestra.plugin.core.log.Log"])
    })

    it("renders a test scope as a Test pill + its Namespace pill", () => {
        const w = mountChip({kind: "TEST", namespace: "company.team", testId: "suite-1"})
        expect(w.text()).toContain("Test:")
        expect(ids(w)).toEqual(["suite-1", "company.team"])
    })

    it("renders nothing when the scope carries no usable fields", () => {
        expect(mountChip({kind: "FLOW"}).find("[data-test=\"copilot-context-chip\"]").exists()).toBe(false)
    })

    it("emits remove with only the dismissed pill's part", async () => {
        const w = mountChip({kind: "FLOW", namespace: "company.team", flowId: "my-flow"})
        // pills render flow first, namespace second — dismiss the namespace pill.
        await w.findAll(".close")[1].trigger("click")
        expect(w.emitted("remove")).toEqual([["namespace"]])
    })
})
