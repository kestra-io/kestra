import {apiUrlWithoutTenants} from "override/utils/route";
import {SchemasSettings} from "monaco-yaml";

export const yamlSchemas: () => SchemasSettings[] = () => [
    {
        fileMatch: ["flow-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/flow`
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/task`
    },
    {
        fileMatch: ["template-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/template`
    },
    {
        fileMatch: ["trigger-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/trigger`
    },
    {
        fileMatch: ["plugindefault-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/plugindefault?arrayOf=true`
    },
    {
        fileMatch: ["dashboard-*.yaml"],
        uri: `${apiUrlWithoutTenants()}/plugins/schemas/dashboard`
    }
]
