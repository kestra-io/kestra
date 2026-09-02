import {describe, expect, it} from "vitest"
import {buildDisplayContext, resolveForDisplay} from "./displayExpression"

const flow = {
    id: "my-flow",
    namespace: "company.team",
    variables: {region: "us-east-1", nested: {zone: "b"}, retries: 3, tags: ["a", "b"]},
}

const execution = {
    id: "4Xw",
    state: {current: "SUCCESS", startDate: "2026-09-02T10:00:00Z"},
    inputs: {bucket: "my-bucket"},
    trigger: {variables: {uri: "s3://in/file.csv"}},
    labels: [{key: "env", value: "prod"}],
    taskRunList: [
        {id: "run-1", taskId: "extract", outputs: {uri: "s3://out/extract.csv"}},
        {id: "iter-parent", taskId: "each", outputs: {}},
        {id: "iter-1", taskId: "load", parentTaskRunId: "iter-parent", value: "a", outputs: {uri: "s3://out/a.csv"}},
        {id: "iter-2", taskId: "load", parentTaskRunId: "iter-parent", value: "b", outputs: {uri: "s3://out/b.csv"}},
    ],
}

describe("resolveForDisplay", () => {
    it("should substitute each occurrence when a value embeds several expressions", () => {
        const context = buildDisplayContext(flow)

        expect(resolveForDisplay("prefix-{{ vars.region }}/{{ vars.nested.zone }}", context))
            .toBe("prefix-us-east-1/b")
    })

    it("should resolve flow metadata and non-string variables", () => {
        const context = buildDisplayContext(flow)

        expect(resolveForDisplay("{{ flow.namespace }}.{{ flow.id }}", context)).toBe("company.team.my-flow")
        expect(resolveForDisplay("{{ vars.retries }}", context)).toBe("3")
        expect(resolveForDisplay("{{ vars.tags }}", context)).toBe("[\"a\",\"b\"]")
    })

    it("should mask a secret rather than resolve it", () => {
        expect(resolveForDisplay("{{ secret('AWS_ACCESS_KEY_ID') }}", buildDisplayContext(flow)))
            .toBe("[secret: AWS_ACCESS_KEY_ID]")
    })

    it.each([
        "{{ vars.unknown }}",
        "{{ vars.region | upper }}",
        "{{ kv('key') }}",
        "{{ envs.region }}",
        "{{ vars.tags[0] }}",
        "{{ vars.retries + 1 }}",
        "{% if vars.region %}{{ vars.region }}{% endif %}",
    ])("should keep %s raw rather than guess a value", (value) => {
        expect(resolveForDisplay(value, buildDisplayContext(flow))).toBe(value)
    })

    it("should keep run-time expressions raw before an execution and resolve them during one", () => {
        const beforeRun = buildDisplayContext(flow)
        const duringRun = buildDisplayContext(flow, execution)

        expect(resolveForDisplay("{{ inputs.bucket }}", beforeRun)).toBe("{{ inputs.bucket }}")
        expect(resolveForDisplay("{{ outputs.extract.uri }}", beforeRun)).toBe("{{ outputs.extract.uri }}")
        expect(resolveForDisplay("{{ execution.id }}", beforeRun)).toBe("{{ execution.id }}")
        expect(resolveForDisplay("{{ trigger.uri }}", beforeRun)).toBe("{{ trigger.uri }}")

        expect(resolveForDisplay("{{ inputs.bucket }}", duringRun)).toBe("my-bucket")
        expect(resolveForDisplay("{{ outputs.extract.uri }}", duringRun)).toBe("s3://out/extract.csv")
        expect(resolveForDisplay("{{ execution.id }}/{{ execution.state }}", duringRun)).toBe("4Xw/SUCCESS")
        expect(resolveForDisplay("{{ trigger.uri }}", duringRun)).toBe("s3://in/file.csv")
        expect(resolveForDisplay("{{ labels.env }}", duringRun)).toBe("prod")
    })

    it("should keep a looped task's outputs raw, since no single iteration is the value", () => {
        expect(resolveForDisplay("{{ outputs.load.uri }}", buildDisplayContext(flow, execution)))
            .toBe("{{ outputs.load.uri }}")
    })
})
