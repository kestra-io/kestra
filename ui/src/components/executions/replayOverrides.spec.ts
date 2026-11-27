import {describe, expect, it} from "vitest"
import {findTask, ownTemplates, patchedOutputs, readPaths} from "./replayOverrides"

describe("readPaths", () => {
    it("lists the outputs read with literal keys, dot or bracket", () => {
        expect(readPaths([
            "{{ outputs.fetch.values.code | trim }} in {{ outputs.fetch['values'].region }}",
            "{{ outputs['my-task'].uri }}",
        ]).map(path => path.path)).toEqual([
            "outputs.fetch.values.code",
            "outputs.fetch.values.region",
            "outputs['my-task'].uri",
        ])
    })

    it("leaves out a chain with a dynamic key, and its prefix", () => {
        expect(readPaths(["{{ outputs.fetch.values[taskrun.value] }} {{ outputs.loop[parent.taskrun.value].code }}"])).toEqual([])
    })

    it("only reads print blocks", () => {
        expect(readPaths([
            "{% if outputs.fetch.values.flag %}{{ outputs.fetch.values.code }}{% endif %}{# outputs.fetch.values.hidden #}",
            "outputs.fetch.values.literal",
        ]).map(path => path.path)).toEqual(["outputs.fetch.values.code"])
    })

    it("ignores outputs of other variables", () => {
        expect(readPaths(["{{ taskrun.outputs.code }} {{ trigger.outputs.code }}"])).toEqual([])
    })
})

describe("ownTemplates", () => {
    it("keeps the properties of a flowable and skips the tasks it holds", () => {
        const flow = {tasks: [{id: "check", type: "io.kestra.plugin.core.flow.If", condition: "{{ outputs.fetch.values.ok }}", then: [{id: "inner", type: "x", message: "{{ outputs.fetch.values.code }}"}]}]}

        expect(ownTemplates(findTask(flow, "check")!)).toEqual(["check", "io.kestra.plugin.core.flow.If", "{{ outputs.fetch.values.ok }}"])
    })
})

describe("patchedOutputs", () => {
    it("replaces each path on a copy, keeping the other keys", () => {
        const outputs = {values: {code: "KR-0412 ", qty: -12}}

        expect(patchedOutputs(outputs, [{keys: ["values", "code"], value: "KR-0412"}])).toEqual({values: {code: "KR-0412", qty: -12}})
        expect(outputs.values.code).toBe("KR-0412 ")
    })
})
