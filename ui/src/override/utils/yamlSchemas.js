import {apiUrl} from "override/utils/route";

export const yamlSchemas = (store) => [
    {
        fileMatch: ["flow-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/flow`]
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/task`]
    },
    {
        fileMatch: ["template-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/template`]
    },
    {
        fileMatch: ["trigger-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/trigger`]
    },
    {
        fileMatch: ["plugindefault-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/plugindefault?arrayOf=true`]
    },
    {
        fileMatch: ["dashboard-*.yaml"],
        uri: [`${apiUrl(store)}/plugins/schemas/dashboard`]
    }
]
