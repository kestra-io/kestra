import {defineConfig} from "@hey-api/openapi-ts";

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
        asClass: true,
        paramsStructure: "flat",
    }
  ],
});