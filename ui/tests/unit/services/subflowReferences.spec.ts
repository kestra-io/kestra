import {describe, it, expect} from "vitest"
import {resolveSubflowLinks} from "../../../src/services/subflowReferences"

describe("resolveSubflowLinks", () => {
    it("links the flowId value of a Subflow task to the referenced flow", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: call_subflow",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: other.namespace",
            "    flowId: child_flow",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(links[0].target).toEqual({namespace: "other.namespace", flowId: "child_flow"})

        const linkedValue = source.slice(links[0].range[0], links[0].range[1])
        expect(linkedValue).toBe("child_flow")
    })

    it("returns no links when the flow has no subflow task", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: log",
            "    type: io.kestra.plugin.core.log.Log",
            "    message: hello",
        ].join("\n")

        expect(resolveSubflowLinks(source)).toEqual([])
    })

    it("skips references whose flowId or namespace is a templated expression", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: dynamic_subflow",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: company.team",
            "    flowId: \"{{ inputs.target }}\"",
        ].join("\n")

        expect(resolveSubflowLinks(source)).toEqual([])
    })

    it("links the referenced flow of a ForEachItem task", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: each",
            "    type: io.kestra.plugin.core.flow.ForEachItem",
            "    items: \"{{ inputs.file }}\"",
            "    namespace: other.namespace",
            "    flowId: child_flow",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(links[0].target).toEqual({namespace: "other.namespace", flowId: "child_flow"})
    })

    it("links each subflow task independently when several are present", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: first",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: ns.one",
            "    flowId: flow_one",
            "  - id: second",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: ns.two",
            "    flowId: flow_two",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(2)
        const targets = links.map((link) => link.target)
        expect(targets).toContainEqual({namespace: "ns.one", flowId: "flow_one"})
        expect(targets).toContainEqual({namespace: "ns.two", flowId: "flow_two"})

        const flowOneLink = links.find((link) => source.slice(link.range[0], link.range[1]) === "flow_one")
        expect(flowOneLink?.target).toEqual({namespace: "ns.one", flowId: "flow_one"})
    })

    it("links the legacy Subflow task type alias", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: legacy",
            "    type: io.kestra.core.tasks.flows.Subflow",
            "    namespace: other.namespace",
            "    flowId: child_flow",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(links[0].target).toEqual({namespace: "other.namespace", flowId: "child_flow"})
    })

    it.each([
        ["unclosed flow sequence", "tasks:\n  - id: x\n    type: io.kestra.plugin.core.flow.Subflow\n    namespace: ["],
        ["dangling key", "id: parent\nnamespace:"],
        ["tabs and colons garbage", ":::\n\t\tbad: : :"],
        ["just a quote", "\""],
        ["empty string", ""],
        ["only whitespace", "   \n  \n"],
    ])("never throws on malformed YAML (%s)", (_label, source) => {
        expect(() => resolveSubflowLinks(source)).not.toThrow()
        expect(Array.isArray(resolveSubflowLinks(source))).toBe(true)
    })

    it("links the inner value of a double-quoted flowId, excluding the quotes", () => {
        const source = [
            "tasks:",
            "  - id: s",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: \"my.ns\"",
            "    flowId: \"child_flow\"",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(source.slice(links[0].range[0], links[0].range[1])).toBe("child_flow")
        expect(links[0].target).toEqual({namespace: "my.ns", flowId: "child_flow"})
    })

    it("skips a Subflow task whose flowId is an empty string", () => {
        const source = [
            "tasks:",
            "  - id: s",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: ns",
            "    flowId: \"\"",
        ].join("\n")

        expect(resolveSubflowLinks(source)).toEqual([])
    })

    it("links the inner value of a single-quoted flowId, excluding the quotes", () => {
        const source = [
            "tasks:",
            "  - id: s",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: 'my.ns'",
            "    flowId: 'child_flow'",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(source.slice(links[0].range[0], links[0].range[1])).toBe("child_flow")
        expect(links[0].target).toEqual({namespace: "my.ns", flowId: "child_flow"})
    })

    it("does not link a numeric (non-string) flowId", () => {
        const source = [
            "tasks:",
            "  - id: s",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    namespace: ns",
            "    flowId: 123",
        ].join("\n")

        expect(resolveSubflowLinks(source)).toEqual([])
    })

    it("returns no links when namespace is omitted but flowId is present", () => {
        const source = [
            "tasks:",
            "  - id: s",
            "    type: io.kestra.plugin.core.flow.Subflow",
            "    flowId: child_flow",
        ].join("\n")

        expect(resolveSubflowLinks(source)).toEqual([])
    })

    it("links a subflow task nested inside a flowable task", () => {
        const source = [
            "id: parent",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: sequential",
            "    type: io.kestra.plugin.core.flow.Sequential",
            "    tasks:",
            "      - id: nested_subflow",
            "        type: io.kestra.plugin.core.flow.Subflow",
            "        namespace: other.namespace",
            "        flowId: child_flow",
        ].join("\n")

        const links = resolveSubflowLinks(source)

        expect(links).toHaveLength(1)
        expect(links[0].target).toEqual({namespace: "other.namespace", flowId: "child_flow"})
    })
})
