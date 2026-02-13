import {defineConfig} from "@hey-api/openapi-ts";
import {defineKestraHeyConfig} from "./heyapi-sdk-plugin";

const generateHash = (str: string) => {
  let hash = 0;
  for (const char of str) {
    hash = (hash << 5) - hash + char.charCodeAt(0);
    hash |= 0; // Constrain to 32bit integer
  }
  return hash.toString(16).replace("-", "0");
};

export default defineConfig({
  input: "../openapi.yml",
  output: {
    path: "./src/generated/kestra-api",
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
    defineKestraHeyConfig({
        output: "./src/generated/kestra-heyapi-sdk",
    })
  ],
});