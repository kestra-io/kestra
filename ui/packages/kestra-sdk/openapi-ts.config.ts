import type { UserConfig } from "@hey-api/openapi-ts";
import * as path from "path";
import { fileURLToPath } from "url";
import { defineConfigKestraHeyOptionalTenant } from "@kestra-io/hey-api-plugin";

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

// Input is the OSS-only spec, produced by `./gradlew :webserver:generateOpenapiSpec`.
//
// The generated output under ./src/openapi is COMMITTED to git — it is not regenerated in the
// fast (npm) CI. Regeneration is an explicit, Gradle-bound step run locally or by the
// `sdk-drift-check` workflow. The final postProcess stamps the spec hash at the bottom of the
// generated index so that workflow can detect drift cheaply. See README.md.
//
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
            {
                // Shared bin from @kestra-io/hey-api-plugin — stamps sha256(openapi.yml) at the
                // bottom of the generated index for the drift-check workflow to compare against.
                command: "npx",
                args: ["hey-api-stamp-hash", "../../../openapi.yml", "src/openapi/index.ts"],
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
