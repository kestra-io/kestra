import {afterEach, describe, expect, it} from "vitest"

globalThis.MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}) as unknown as Worker,
}

import * as monaco from "monaco-editor/editor/editor.api.js"
import {providePebbleDefinition} from "./pebbleDefinitionProvider"

const FLOW = [
    "id: pipeline",
    "namespace: company.team",
    "inputs:",
    "  - id: file",
    "    type: FILE",
    "tasks:",
    "  - id: extract",
    "    type: io.kestra.plugin.core.http.Download",
    "    uri: \"{{ inputs.file }}\"",
    "  - id: log",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: \"{{ outputs.extract.uri }}\"",
].join("\n")

const models: monaco.editor.ITextModel[] = []
const createModel = (path: string): monaco.editor.ITextModel => {
    const model = monaco.editor.createModel(FLOW, "yaml", monaco.Uri.parse(`inmemory://model/${path}`))
    models.push(model)
    return model
}

const positionInside = (model: monaco.editor.ITextModel, lineNumber: number, token: string): monaco.IPosition =>
    ({lineNumber, column: model.getLineContent(lineNumber).indexOf(token) + 2})

const textOf = (model: monaco.editor.ITextModel, range: monaco.IRange | undefined): string | undefined =>
    range && model.getValueInRange(range)

afterEach(() => {
    models.splice(0).forEach((model) => model.dispose())
})

describe("providePebbleDefinition against a real Monaco model", () => {
    it("links an inputs reference to the input id, selecting only the referenced token at the origin", () => {
        const model = createModel("flow-test.yaml")
        const links = providePebbleDefinition(model, positionInside(model, 9, "file"))
        expect(links).toHaveLength(1)
        expect(textOf(model, links?.[0].originSelectionRange)).toBe("file")
        expect(textOf(model, links?.[0].targetSelectionRange)).toBe("file")
        expect(textOf(model, links?.[0].range)).toBe("file")
        expect(links?.[0].range.startLineNumber).toBe(4)
        expect(links?.[0].uri.toString()).toBe(model.uri.toString())
    })

    it("links an outputs reference to the task id", () => {
        const model = createModel("flow-test.yaml")
        const links = providePebbleDefinition(model, positionInside(model, 12, "extract"))
        expect(textOf(model, links?.[0].originSelectionRange)).toBe("extract")
        expect(textOf(model, links?.[0].targetSelectionRange)).toBe("extract")
        expect(links?.[0].range.startLineNumber).toBe(7)
    })

    it("follows an edit of the target id instead of serving the previous resolution", () => {
        const model = createModel("flow-test.yaml")
        const position = positionInside(model, 9, "file")
        expect(providePebbleDefinition(model, position)).toHaveLength(1)
        model.setValue(FLOW.replace("- id: file", "- id: other"))
        expect(providePebbleDefinition(model, position)).toBeNull()
    })

    it("yields null for a model that is not a flow", () => {
        const model = createModel("file-other.yaml")
        expect(providePebbleDefinition(model, positionInside(model, 9, "file"))).toBeNull()
    })
})
