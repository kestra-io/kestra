import {apiRoot} from "../../utils/axios";

export const yamlSchemas = [
    {
        fileMatch: ["flow-*.yaml"],
        uri: [`${apiRoot}plugins/schemas/flow`]
    },
    {
        fileMatch: ["task-*.yaml"],
        uri: [`${apiRoot}plugins/schemas/task`]
    },
    {
        fileMatch: ["template-*.yaml"],
        uri: [`${apiRoot}plugins/schemas/template`]
    }
]
