import {defineConfig} from "@hey-api/openapi-ts";

const capitalize = (s: string) => s.charAt(0).toUpperCase() + s.slice(1);
const deCapitalize = (s: string) => s.charAt(0).toLowerCase() + s.slice(1);

export default defineConfig({
  input: "../webserver/build/classes/java/main/META-INF/swagger/kestra.yml",
  output: {
    path: "./src/generated/kestra-api",
    lint: "eslint"
  },

  plugins: [{
        name: "@hey-api/client-axios",
    },
    {
        name: "@hey-api/sdk",
        paramsStructure: "flat",
        methodNameBuilder(operation) {
            return `${deCapitalize(operation.tags?.[0] ?? "")}${capitalize(operation.operationId ?? "")}`;
        }
    }
  ],
});