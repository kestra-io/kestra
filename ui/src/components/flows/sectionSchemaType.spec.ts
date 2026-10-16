import {describe, it, expect, vi} from "vitest"
import {sectionSchemaType} from "./sectionSchemaType"
import {yamlSchemas} from "../../override/utils/yamlSchemas"

vi.mock("override/utils/route", () => ({apiUrlWithoutTenants: () => "/api/v1"}))

function schemaFor(modelName: string): string | undefined {
    return yamlSchemas().find(({fileMatch}) =>
        fileMatch?.some(glob => new RegExp(`^${glob.replace(/\./g, "\\.").replace(/\*/g, ".*")}$`).test(modelName)),
    )?.uri.replace(/\?.*$/, "")
}

describe("sectionSchemaType", () => {
    it.each([
        ["tasks", "task"],
        ["TASKS", "task"],
        ["errors", "task"],
        ["finally", "task"],
        ["afterExecution", "task"],
        ["triggers", "trigger"],
        ["TRIGGERS", "trigger"],
    ])("maps the %s section to the %s schema type", (section, schemaType) => {
        expect(sectionSchemaType(section)).toBe(schemaType)
    })

    it.each([
        ["tasks", "/api/v1/plugins/schemas/task"],
        ["errors", "/api/v1/plugins/schemas/task"],
        ["finally", "/api/v1/plugins/schemas/task"],
        ["afterExecution", "/api/v1/plugins/schemas/task"],
        ["triggers", "/api/v1/plugins/schemas/trigger"],
    ])("binds an editor model of the %s section to a schema", (section, schema) => {
        // Monaco names the model `<schemaType>-<path>.yaml`
        expect(schemaFor(`${sectionSchemaType(section)}-${section}[0].yaml`)).toBe(schema)
    })
})
