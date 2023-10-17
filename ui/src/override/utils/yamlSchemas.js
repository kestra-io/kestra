import {apiUrlWithoutTenants} from "override/utils/route";

export const yamlSchemas = (store) => [
    {
        fileMatch: ["flow-*.yaml"],
        uri: [`${apiUrlWithoutTenants()}/plugins/schemas/flow`]
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: [`${apiUrlWithoutTenants()}/plugins/schemas/task`]
    },
    {
        fileMatch: ["template-*.yaml"],
        uri: [`${apiUrlWithoutTenants()}/plugins/schemas/template`]
    },
    {
        fileMatch: ["trigger-*.yaml"],
        uri: [`${apiUrlWithoutTenants()}/plugins/schemas/trigger`]
    }
]
