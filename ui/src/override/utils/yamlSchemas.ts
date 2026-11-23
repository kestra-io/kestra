import {apiUrlWithoutTenants} from "override/utils/route"
import {SchemasSettings} from "monaco-yaml"

export const yamlSchemas: () => SchemasSettings[] = () => [
    {
        fileMatch: ["flow-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/flow?includeCatalog=true`,
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/task?includeCatalog=true`,
    },
    {
        fileMatch: ["trigger-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/trigger?includeCatalog=true`,
    },
    {
        fileMatch: ["plugindefault-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/plugindefault?arrayOf=true&includeCatalog=true`,
    },
    {
        fileMatch: ["dashboard-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/dashboard`,
    },
]
