import {afterEach, describe, expect, it} from "vitest"

globalThis.MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}) as unknown as Worker,
}

import * as monaco from "monaco-editor/editor/editor.api.js"
import {providePebbleDefinition, type OutputPropertiesLoader} from "./pebbleDefinitionProvider"

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
    "    message: \"{{ outputs.extract.uri }} {{ trigger.date }} {{ trigger.nope }}\"",
    "triggers:",
    "  - id: hook",
    "    type: io.kestra.plugin.core.trigger.Webhook",
    "  - id: daily",
    "    type: io.kestra.plugin.core.trigger.Schedule",
].join("\n")

const SCHEDULE_OUTPUTS: Record<string, {type: string}> = {date: {type: "string"}}
const loader: OutputPropertiesLoader = (type) =>
    Promise.resolve(type === "io.kestra.plugin.core.trigger.Schedule" ? SCHEDULE_OUTPUTS : {})

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
    it("links an inputs reference to the input id, selecting only the referenced token at the origin", async () => {
        const model = createModel("flow-test.yaml")
        const links = await providePebbleDefinition(model, positionInside(model, 9, "file"), loader)
        expect(links).toHaveLength(1)
        expect(textOf(model, links?.[0].originSelectionRange)).toBe("file")
        expect(textOf(model, links?.[0].targetSelectionRange)).toBe("file")
        expect(textOf(model, links?.[0].range)).toBe("file")
        expect(links?.[0].range.startLineNumber).toBe(4)
        expect(links?.[0].uri.toString()).toBe(model.uri.toString())
    })

    it("links an outputs reference to the task id", async () => {
        const model = createModel("flow-test.yaml")
        const links = await providePebbleDefinition(model, positionInside(model, 12, "extract"), loader)
        expect(textOf(model, links?.[0].originSelectionRange)).toBe("extract")
        expect(textOf(model, links?.[0].targetSelectionRange)).toBe("extract")
        expect(links?.[0].range.startLineNumber).toBe(7)
    })

    it("follows an edit of the target id instead of serving the previous resolution", async () => {
        const model = createModel("flow-test.yaml")
        const position = positionInside(model, 9, "file")
        expect(await providePebbleDefinition(model, position, loader)).toHaveLength(1)
        model.setValue(FLOW.replace("- id: file", "- id: other"))
        expect(await providePebbleDefinition(model, position, loader)).toBeNull()
    })

    it("links a trigger reference only to the triggers whose schema declares it", async () => {
        const model = createModel("flow-test.yaml")
        const links = await providePebbleDefinition(model, positionInside(model, 12, "date"), loader)
        expect(links?.map((link) => textOf(model, link.targetSelectionRange))).toEqual(["daily"])
        expect(await providePebbleDefinition(model, positionInside(model, 12, "nope"), loader)).toBeNull()
    })

    it("yields null for a model that is not a flow", async () => {
        const model = createModel("file-other.yaml")
        expect(await providePebbleDefinition(model, positionInside(model, 9, "file"), loader)).toBeNull()
    })
})
