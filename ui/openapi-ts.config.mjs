import {defineKestraHeyConfig} from "./heyapi-sdk-plugin";

/**
 * Generates a unique hash for a given string, suitable for use as a method name.
 * @param {string} str 
 * @returns {string} A unique hash string derived from the input string.
 */
const generateHash = (str) => {
  let hash = 0;
  for (const char of str) {
    hash = (hash << 5) - hash + char.charCodeAt(0);
    hash |= 0; // Constrain to 32bit integer
  }
  return hash.toString(16).replace("-", "0");
};

/**
 * @type {import("@hey-api/openapi-ts").UserConfig}
 */
export default {
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
};