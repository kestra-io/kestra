import {describe, expect, it} from "vitest"
import {pebbleReferenceAtPosition, resolveFlowDefinition, type PebbleReference} from "./pebbleDefinitionProvider"

const FLOW = `id: pipeline
namespace: company.team

labels:
  env: dev
  team: data

inputs:
  - id: file
    type: FILE
  - id: "quoted"
    type: STRING
  - id: form
    type: FORM
    inputs:
      - id: name
        type: STRING
      - id: age
        type: INT

variables:
  bucket: s3://data

tasks:
  - id: extract
    type: io.kestra.plugin.core.http.Download
    uri: https://example.com
  - id: my-task
    type: io.kestra.plugin.core.log.Log
    message: "{{ inputs.file }}"
  - id: parallel
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: nested
        type: io.kestra.plugin.core.log.Log
        message: hi
      - id: dup
        type: io.kestra.plugin.core.log.Log
        message: first
  - id: dup
    type: io.kestra.plugin.core.log.Log
    message: second

errors:
  - id: on_error
    type: io.kestra.plugin.core.log.Log
    message: "{{ outputs.extract.uri }}"
`

/** 1-based column of the second character of `token`, so the cursor sits inside it. */
const columnInside = (line: string, token: string): number => line.indexOf(token) + 2

const referenceAt = (line: string, token: string): PebbleReference => {
    const ref = pebbleReferenceAtPosition(line, columnInside(line, token))
    if (!ref) {
        throw new Error(`no pebble reference on "${token}" in "${line}"`)
    }
    return ref
}

const resolvedTexts = (line: string, token: string): string[] =>
    resolveFlowDefinition(FLOW, referenceAt(line, token)).map(([start, end]) => FLOW.slice(start, end))

const lineOfOffset = (offset: number): number => FLOW.slice(0, offset).split("\n").length

describe("pebbleReferenceAtPosition", () => {
    it("splits a dotted chain with a bracket string segment and selects only the identifier under the cursor", () => {
        const line = "    uri: \"{{ outputs.extract.outputFiles['data.csv'] }}\""
        const ref = referenceAt(line, "extract")
        expect(ref).toMatchObject({root: "outputs", segments: ["extract", "outputFiles", "data.csv"], segmentIndex: 0})
        expect(line.slice(ref.startColumn - 1, ref.endColumn - 1)).toBe("extract")
    })

    it("points at the string inside the brackets when the cursor is on a bracket segment", () => {
        const line = "{{ outputs.extract.outputFiles['data.csv'] }}"
        const ref = referenceAt(line, "data.csv")
        expect(ref.segmentIndex).toBe(2)
        expect(line.slice(ref.startColumn - 1, ref.endColumn - 1)).toBe("data.csv")
    })

    it("reports the root word with segmentIndex -1", () => {
        const ref = referenceAt("{{ inputs.file }}", "inputs")
        expect(ref).toMatchObject({root: "inputs", segments: ["file"], segmentIndex: -1})
    })

    it("returns undefined outside any pebble block, including after the block closes", () => {
        expect(pebbleReferenceAtPosition("message: inputs.file", columnInside("message: inputs.file", "file"))).toBeUndefined()
        const line = "{{ inputs.file }} and inputs.other"
        expect(pebbleReferenceAtPosition(line, columnInside(line, "other"))).toBeUndefined()
    })

    it("returns undefined when the cursor sits on whitespace inside the block", () => {
        expect(pebbleReferenceAtPosition("{{  inputs.file }}", 3)).toBeUndefined()
    })
})

describe("resolveFlowDefinition", () => {
    it("resolves {{ inputs.x }} to the id scalar of the root input", () => {
        expect(resolvedTexts("message: \"{{ inputs.file }}\"", "file")).toEqual(["file"])
    })

    it("resolves a reference inside a {% if %} statement block", () => {
        expect(resolvedTexts("{% if inputs.file %}", "file")).toEqual(["file"])
    })

    it("excludes the quotes of a quoted id scalar", () => {
        const [range] = resolveFlowDefinition(FLOW, referenceAt("{{ inputs.quoted }}", "quoted"))
        expect(FLOW.slice(range[0], range[1])).toBe("quoted")
        expect(FLOW.slice(range[0] - 1, range[1] + 1)).toBe("\"quoted\"")
    })

    it("resolves outputs['my-task'] bracket syntax with a dash in the id", () => {
        expect(resolvedTexts("{{ outputs['my-task'].value }}", "my-task")).toEqual(["my-task"])
    })

    it("resolves to the task when the cursor is on a later segment of an outputs chain", () => {
        expect(resolvedTexts("{{ outputs.extract.uri }}", "uri")).toEqual(["extract"])
    })

    it("resolves the child of a FORM input from the second segment", () => {
        expect(resolvedTexts("{{ inputs.form.age }}", "age")).toEqual(["age"])
    })

    it("does not descend into a non-FORM input", () => {
        expect(resolvedTexts("{{ inputs.file.name }}", "name")).toEqual([])
    })

    it("finds a task declared under errors", () => {
        expect(resolvedTexts("{{ outputs.on_error.x }}", "on_error")).toEqual(["on_error"])
    })

    it("finds a task nested inside a flowable", () => {
        expect(resolvedTexts("{{ outputs.nested.x }}", "nested")).toEqual(["nested"])
    })

    it("returns one range per task sharing a duplicated id", () => {
        const ranges = resolveFlowDefinition(FLOW, referenceAt("{{ outputs.dup.x }}", "dup"))
        expect(ranges.map(([start, end]) => FLOW.slice(start, end))).toEqual(["dup", "dup"])
        expect(lineOfOffset(ranges[1][0])).toBeGreaterThan(lineOfOffset(ranges[0][0]))
    })

    it("resolves vars.x to the key of the variables entry", () => {
        expect(resolvedTexts("{{ vars.bucket }}", "bucket")).toEqual(["bucket"])
    })

    it("resolves labels.x to the key of the labels entry", () => {
        expect(resolvedTexts("{{ labels.team }}", "team")).toEqual(["team"])
    })

    it("returns nothing when the cursor is on the root word", () => {
        expect(resolvedTexts("{{ inputs.file }}", "inputs")).toEqual([])
        expect(resolvedTexts("{{ outputs.extract }}", "outputs")).toEqual([])
    })

    it("returns nothing for an unknown id or an unsupported root", () => {
        expect(resolvedTexts("{{ inputs.missing }}", "missing")).toEqual([])
        expect(resolvedTexts("{{ outputs.missing.x }}", "missing")).toEqual([])
        expect(resolvedTexts("{{ trigger.date }}", "date")).toEqual([])
    })
})
