import {describe, it, expect, vi} from "vitest"
import {yamlSchemas} from "./yamlSchemas"

vi.mock("override/utils/route", () => ({apiUrlWithoutTenants: () => "/api/v1"}))

// Monaco names an editor model `<schemaType>-<path>.yaml`, and monaco-yaml binds a schema by matching that name.
function hasSchemaFor(modelName: string): boolean {
    return yamlSchemas().some(({fileMatch}) =>
        fileMatch?.some(glob => new RegExp(`^${glob.replace(/\./g, "\\.").replace(/\*/g, ".*")}$`).test(modelName)),
    )
}

const sources = import.meta.glob<string>("/src/**/*.vue", {query: "?raw", import: "default", eager: true})

describe("yamlSchemas", () => {
    // An editor that declares a `schemaType` no entry matches silently loses all of its suggestions.
    it("binds a schema to every literal `schemaType` an editor declares", () => {
        const declared = new Set<string>()
        for (const source of Object.values(sources)) {
            for (const [, schemaType] of source.matchAll(/(?<![:\w])schemaType="([a-z_]+)"/g)) {
                declared.add(schemaType)
            }
        }

        expect([...declared].filter(schemaType => !hasSchemaFor(`${schemaType}-.yaml`))).toEqual([])
        expect(declared.size).toBeGreaterThan(0)
    })
})
