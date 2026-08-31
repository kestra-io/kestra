import {describe, it, expect} from "vitest"
import {getType} from "../../../src/components/no-code/components/tasks/getTaskComponent"

// Drift guard for the flow properties panel: each flow-level field of the
// spec must keep resolving to the form editor family the panel was designed
// around. Shapes mirror what GET /api/v1/plugins/schemas/flow serves.
const definitions = {}

describe("flow-level field type mapping", () => {
    it("variables (open object) edits as a dict", () => {
        expect(getType({type: "object"}, definitions, "variables")).toBe("dict")
    })

    it("labels (list-or-map anyOf) edits as a dict", () => {
        const schema = {anyOf: [{type: "array"}, {type: "object"}]}
        expect(getType(schema, definitions, "labels")).toBe("dict")
    })

    it("concurrency ($ref object) edits as a complex group", () => {
        const schema = {$ref: "#/definitions/io.kestra.core.models.flows.Concurrency"}
        expect(getType(schema, definitions, "concurrency")).toBe("complex")
    })

    it("workerSelector (allOf-wrapped $ref) edits as a complex group", () => {
        const schema = {allOf: [
            {$ref: "#/definitions/io.kestra.core.models.tasks.WorkerSelector"},
            {markdownDescription: "Routing requirements (tags + fallback) for this flow."},
        ]}
        expect(getType(schema, definitions, "workerSelector")).toBe("complex")
    })

    it("retry (anyOf of retry policies) edits as an any-of selector", () => {
        const schema = {anyOf: [
            {allOf: [{$ref: "#/definitions/io.kestra.core.models.tasks.retrys.Constant-2"}, {title: "Retry"}]},
            {allOf: [{$ref: "#/definitions/io.kestra.core.models.tasks.retrys.Exponential-2"}, {title: "Retry"}]},
            {allOf: [{$ref: "#/definitions/io.kestra.core.models.tasks.retrys.Random-2"}, {title: "Retry"}]},
        ]}
        expect(getType(schema, definitions, "retry")).toBe("any-of")
    })

    for (const key of ["sla", "checks", "quotas"]) {
        it(`${key} (array of governed objects) edits as an array`, () => {
            const schema = {type: "array", items: {allOf: [{$ref: `#/definitions/${key}`}, {$dynamic: false}]}}
            expect(getType(schema, definitions, key)).toBe("array")
        })
    }

    it("outputs (array of Output) edits as an array", () => {
        const schema = {type: "array", items: {allOf: [{$ref: "#/definitions/io.kestra.core.models.flows.Output"}, {$dynamic: true}]}}
        expect(getType(schema, definitions, "outputs")).toBe("array")
    })

    it("disabled (boolean) edits as a switch", () => {
        expect(getType({type: "boolean", default: false}, definitions, "disabled")).toBe("boolean")
    })
})
