import {describe, it, expect, vi} from "vitest"
import {sectionSchemaType} from "./sectionSchemaType"
import {yamlSchemas} from "../../override/utils/yamlSchemas"

vi.mock("override/utils/route", () => ({apiUrlWithoutTenants: () => "/api/v1"}))

// Monaco names an editor model `<schemaType>-<path>.yaml`, and monaco-yaml binds a schema by matching that name.
function schemaFor(modelName: string): string | undefined {
    return yamlSchemas().find(({fileMatch}) =>
        fileMatch?.some((glob) => {
            const [prefix, suffix] = glob.split("*")
            return modelName.startsWith(prefix) && modelName.endsWith(suffix)
        }),
    )?.uri.replace(/\?.*$/, "")
}

describe("sectionSchemaType", () => {
    it.each([
        ["tasks", "/api/v1/plugins/schemas/task"],
        ["TASKS", "/api/v1/plugins/schemas/task"],
        ["errors", "/api/v1/plugins/schemas/task"],
        ["finally", "/api/v1/plugins/schemas/task"],
        ["afterExecution", "/api/v1/plugins/schemas/task"],
        ["triggers", "/api/v1/plugins/schemas/trigger"],
        ["TRIGGERS", "/api/v1/plugins/schemas/trigger"],
    ])("binds an editor model of the %s section to a schema", (section, schema) => {
        expect(schemaFor(`${sectionSchemaType(section)}-${section}[0].yaml`)).toBe(schema)
    })
})
