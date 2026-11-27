import {beforeAll, beforeEach, describe, expect, it, vi} from "vitest"

interface Position {
    line: number
    character: number
}

interface YamlService {
    doValidation(uri: string): Promise<{message: string; range: {start: Position}}[]>
    doComplete(uri: string, position: Position): Promise<{items: {label: string}[]}>
}

type CreateService = (
    ctx: {getMirrorModels: () => {uri: string; version: number; getValue: () => string}[]},
    options: {validate: boolean; completion: boolean; schemas: {uri: string; fileMatch: string[]; schema: object}[]},
) => YamlService

const worker = vi.hoisted(() => ({create: undefined as CreateService | undefined}))

vi.mock("monaco-worker-manager/worker", () => ({
    initialize: (create: CreateService) => {
        worker.create = create
    },
}))

const URI = "file:///flow-test.yaml"
const TYPES = ["io.kestra.Log", "io.kestra.Return", "io.kestra.Sleep", "io.kestra.Sequential"]

let evaluatedTypes: string[] = []

function taskSchema(type: string, properties: Record<string, object>, required: string[]) {
    return {
        type: "object",
        properties: {id: {type: "string"}, type: {const: type}, ...properties},
        required: ["id", "type", ...required],
        // The validator copies an alternative before evaluating it, which reads this once per evaluation.
        get evaluated() {
            evaluatedTypes.push(type)
            return true
        },
    }
}

const schema = {
    type: "object",
    properties: {id: {type: "string"}, tasks: {$ref: "#/definitions/tasks"}},
    definitions: {
        tasks: {
            type: "array",
            items: {
                anyOf: [
                    taskSchema(TYPES[0], {message: {type: "string"}}, ["message"]),
                    taskSchema(TYPES[1], {format: {type: "string"}}, ["format"]),
                    taskSchema(TYPES[2], {duration: {type: "string"}}, ["duration"]),
                    taskSchema(TYPES[3], {tasks: {$ref: "#/definitions/tasks"}}, ["tasks"]),
                ],
            },
        },
    },
}

describe("yaml worker type discriminator", () => {
    let service: YamlService
    let source = "id: warmup\n"
    let version = 0

    const open = (text: string) => {
        source = text
        version++
    }

    beforeAll(async () => {
        await import("monaco-yaml/yaml.worker")
        service = worker.create!(
            {getMirrorModels: () => [{uri: URI, version, getValue: () => source}]},
            {validate: true, completion: true, schemas: [{uri: "https://kestra.io/flow.json", fileMatch: ["flow-*.yaml"], schema}]},
        )
        await service.doValidation(URI)
    })

    beforeEach(() => {
        evaluatedTypes = []
    })

    it("validates a typed task against its own type only, nested tasks included", async () => {
        open([
            "id: flow",
            "tasks:",
            "  - id: log",
            "    type: io.kestra.Log",
            "  - id: group",
            "    type: io.kestra.Sequential",
            "    tasks:",
            "      - id: nested",
            "        type: io.kestra.Return",
        ].join("\n"))

        const diagnostics = await service.doValidation(URI)

        expect(diagnostics.map((d) => `${d.range.start.line}: ${d.message}`)).toEqual([
            "2: Missing property \"message\".",
            "7: Missing property \"format\".",
        ])
        expect([...evaluatedTypes].sort()).toEqual(["io.kestra.Log", "io.kestra.Return", "io.kestra.Sequential"])
    })

    it("still offers every type on the type value of an already typed task", async () => {
        open([
            "id: flow",
            "tasks:",
            "  - id: first",
            "    type: io.kestra.Return",
            "    format: text",
            "  - id: log",
            "    type: io.kestra.Log",
            "    message: hello",
        ].join("\n"))

        const completion = await service.doComplete(URI, {line: 6, character: "    type: io.kestra.Log".length})

        expect(completion.items.map((item) => item.label)).toEqual(expect.arrayContaining(TYPES))
    })
})
