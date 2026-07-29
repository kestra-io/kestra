import {describe, it, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {i18n} from "./_helpers"
import CopilotContextChip from "../../../../../src/components/ai/copilot/CopilotContextChip.vue"

// KsTag stub with a working close button so we can assert the clear emit.
const KsTag = {
    name: "KsTag",
    // `closable` must be typed Boolean so the valueless `<KsTag closable>` attribute resolves to true
    // (an untyped array prop would make it "" — falsy — and hide the close button).
    props: {closable: Boolean, icon: {type: [Object, Function], default: undefined}, size: {type: String, default: undefined}},
    emits: ["close"],
    template: "<span class=\"ks-tag\"><slot /><button v-if=\"closable\" class=\"close\" @click=\"$emit('close')\">x</button></span>",
}
const KsIcon = {name: "KsIcon", template: "<i><slot /></i>"}
// KsId renders the id as a code-styled token; stub it to expose the value + the <code> wrapper.
const KsId = {name: "KsId", props: {value: {type: String, default: ""}, shrink: {type: Boolean, default: true}}, template: "<code class=\"ks-id\">{{ value }}</code>"}

const mountChip = (scope: any) =>
    mount(CopilotContextChip, {props: {scope}, global: {plugins: [i18n], stubs: {KsTag, KsIcon, KsId}}})

describe("CopilotContextChip", () => {
    it("labels a flow scope with its namespace and id (both as code tokens)", () => {
        const w = mountChip({kind: "FLOW", namespace: "company.team", flowId: "my-flow"})
        expect(w.text()).toContain("Flow:")
        // A flow is only unique within its namespace, so both are shown, namespace first.
        expect(w.findAll("code.ks-id").map(c => c.text())).toEqual(["company.team", "my-flow"])
    })

    it("labels an execution scope", () => {
        expect(mountChip({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"}).text())
            .toContain("Execution: exec-1")
    })

    it("labels a namespace scope", () => {
        expect(mountChip({kind: "NAMESPACE", namespace: "company.team"}).text()).toContain("Namespace: company.team")
    })

    it("renders the id as a code-styled token (KsId), like execution ids in tables", () => {
        const w = mountChip({kind: "EXECUTION", namespace: "company.team", flowId: "my-flow", executionId: "exec-1"})
        expect(w.find("code.ks-id").text()).toBe("exec-1")
    })

    it("emits clear when the chip is dismissed", async () => {
        const w = mountChip({kind: "FLOW", flowId: "my-flow"})
        await w.find(".close").trigger("click")
        expect(w.emitted("clear")).toHaveLength(1)
    })
})
