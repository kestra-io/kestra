import type {UserConfig} from "@hey-api/openapi-ts";
import * as path from "path";
import {fileURLToPath} from "url";
import {defineKestraHeyConfig} from "./heyapi-sdk-plugin";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const generateHash = (str: string) => {
    let hash = 0;
    for (const char of str) {
        hash = (hash << 5) - hash + char.charCodeAt(0);
        hash |= 0; // Constrain to 32bit integer
    }
    return hash.toString(16).replace("-", "0");
};

export default {
    input: path.resolve(__dirname, "../../../openapi.yml"),
    output: {
        path: path.resolve(__dirname, "./src"),
        postProcess: ["eslint"],
    },

    plugins: [
        {
            name: "@hey-api/client-axios",
        },
        {
            name: "@hey-api/sdk",
            paramsStructure: "flat",
            operations: {
                methodName(operation) {
                    return `__${generateHash(operation)}__`
                },
            }
        },
        defineKestraHeyConfig()
    ],
} satisfies UserConfig
