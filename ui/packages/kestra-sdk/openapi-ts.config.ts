import type { UserConfig } from "@hey-api/openapi-ts";
import * as path from "path";
import { fileURLToPath } from "url";
import { defineConfigKestraHeyOptionalTenant } from "./heyapi-sdk-plugin";

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

// Input is the OSS-only spec, produced on this same commit by
// `./gradlew :webserver:generateOpenapiSpec` (see ../../../../README's "generate:openapi" script).
// POC scope: unlike client-sdk's pipeline, this does not run the kestra-openapi-sdk-customizer
// sanitizer pass — follow-up work if this spike is adopted.
export default {
    input: path.resolve(__dirname, "../../../openapi.yml"),
    output: {
        path: path.resolve(__dirname, "./src/openapi"),
        postProcess: [
            {
                command: "node",
                args: ["scripts/convert-openapi-sdk-functions.mjs", "{{path}}"],
            },
        ],
    },

    plugins: [
        {
            name: "@hey-api/client-axios",
            throwOnError: true,
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
        // The one openapi-ts plugin for the whole product: EE's kestra-sdk package imports this
        // same module from OSS (see kestra-ee/ui-ee/packages/kestra-sdk/openapi-ts.config.ts)
        // instead of forking its own copy.
        defineConfigKestraHeyOptionalTenant()
    ],
} satisfies UserConfig
