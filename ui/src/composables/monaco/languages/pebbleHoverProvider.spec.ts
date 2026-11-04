import {afterEach, describe, expect, it, vi} from "vitest"

globalThis.MonacoEnvironment = {
    getWorker: () => ({postMessage(){}, terminate(){}, addEventListener(){}, removeEventListener(){}}) as unknown as Worker,
}

import * as monaco from "monaco-editor/editor/editor.api.js"
import {providePebbleHover} from "./pebbleHoverProvider"

const SCRIPT = Array.from({length: 20}, (_, i) => `      echo ${i} \`\`\``).join("\n")

const FLOW = `id: pipeline
namespace: company.team

inputs:
  # Raw export, one row per order.
  - id: file
    type: FILE
    description: |
      The **orders** file.
    required: true

variables:
  # Target bucket.
  bucket: s3://data

tasks:
  - id: extract
    type: io.kestra.plugin.scripts.shell.Commands
    commands: |
${SCRIPT}
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: "{{ inputs.file }} {{ vars.bucket }} {{ outputs.extract.uri }} {{ outputs.extract.missing }} {{ trigger.date }} {{ trigger.nope }} {{ outputs.extract.uri.deep }}"

triggers:
  - id: hook
    type: io.kestra.plugin.core.trigger.Webhook
    key: abc
  - id: daily
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 9 * * *"
`

const SCHEMAS: Record<string, Record<string, {type: string; title: string}>> = {
    "io.kestra.plugin.scripts.shell.Commands": {uri: {type: "string", title: "The downloaded file URI."}},
    "io.kestra.plugin.core.trigger.Webhook": {body: {type: "object", title: "The request body."}},
    "io.kestra.plugin.core.trigger.Schedule": {date: {type: "string", title: "The scheduled date."}},
}

const LOG_LINE = FLOW.split("\n").findIndex((line) => line.includes("message:")) + 1

const models: monaco.editor.ITextModel[] = []

afterEach(() => {
    models.splice(0).forEach((model) => model.dispose())
})

async function hoverOn(token: string, loader = vi.fn((type: string) => Promise.resolve(SCHEMAS[type]))) {
    const model = monaco.editor.createModel(FLOW, "yaml", monaco.Uri.parse(`inmemory://model/flow-${models.length}.yaml`))
    models.push(model)
    const column = model.getLineContent(LOG_LINE).indexOf(token) + 2
    const hover = await providePebbleHover(model, {lineNumber: LOG_LINE, column}, loader)
    return {hover, loader, values: hover?.contents.map((content) => content.value) ?? []}
}

describe("providePebbleHover", () => {
    it("shows an input's YAML without its description, and renders the leading comment and description as untrusted docs", async () => {
        const {hover, values} = await hoverOn("file")

        expect(values[0]).toBe("```yaml\n- id: file\n  type: FILE\n  required: true\n```")
        expect(values[1]).toBe("Raw export, one row per order.\n\nThe **orders** file.")
        expect(hover?.contents[1]).toMatchObject({isTrusted: false, supportHtml: false})
    })

    it("documents a variable with the comment above it, including the first entry whose comment yaml attaches to the map", async () => {
        const {values} = await hoverOn("bucket")

        expect(values).toEqual(["```yaml\nbucket: s3://data\n```", "Target bucket."])
    })

    it("documents an output property from the task's plugin schema", async () => {
        const {values, loader} = await hoverOn("uri")

        expect(loader).toHaveBeenCalledWith("io.kestra.plugin.scripts.shell.Commands")
        expect(values).toEqual(["```yaml\nuri: string\n```", "The downloaded file URI."])
    })

    it("documents a trigger variable from the schema of the triggers that declare it, and names them", async () => {
        const {hover, values} = await hoverOn("date")

        expect(values).toEqual(["```yaml\ndate: string\n```", "The scheduled date.", "$(zap) `daily`"])
        expect(hover?.contents[2]).toMatchObject({supportThemeIcons: true})
    })

    it("shows nothing for a trigger variable no trigger declares, instead of every trigger", async () => {
        const {hover} = await hoverOn("nope")

        expect(hover).toBeNull()
    })

    it("documents an output property only on the property itself, not on a deeper segment", async () => {
        const {values} = await hoverOn("deep")

        expect(values.join("\n")).not.toContain("The downloaded file URI.")
    })

    it("falls back to the task's YAML, capped and fenced past its own backticks, for an output the schema does not declare", async () => {
        const {values} = await hoverOn("missing")
        const lines = values[0].split("\n")

        expect(lines[0]).toBe("````yaml")
        expect(lines[1]).toBe("- id: extract")
        expect(lines.at(-2)).toBe("...")
        expect(lines).toHaveLength(15 + 3)
    })
})
