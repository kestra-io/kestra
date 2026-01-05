import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

import {commit} from "./plugins/commit"
import {codecovVitePlugin} from "@codecov/vite-plugin";

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
        rollupOptions: {
            output: {
                advancedChunks: {
                    groups: [
                        {
                            test: /src\/components\/dashboard/i,
                            name: "dashboard",
                        },
                        {
                            test: /src\/components\/flows/i,
                            name: "flows",
                        },
                        {
                            test: /(shiki\/langs)|(src\/utils\/markdownDeps)/,
                            name: "markdownDeps",
                        },
                    ],
                }
            }
        }
    },
    server: {
        proxy: {
            "^/api": {
                target: "http://localhost:8080",
                ws: true,
                changeOrigin: true
            }
        }
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            "#imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#build/mdc-image-component.mjs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-configs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "@storybook/addon-actions": "storybook/actions",
        },
    },
    plugins: [
        vue({
            template: {
                compilerOptions: {
                    isCustomElement: (tag) => {
                        return tag === "rapi-doc";
                    }
                }
            }
        }),
        commit(),
        codecovVitePlugin({
            enableBundleAnalysis: process.env.CODECOV_TOKEN !== undefined,
            bundleName: "ui",
            uploadToken: process.env.CODECOV_TOKEN,
            telemetry: false
        }),
    ],
    assetsInclude: ["**/*.md"],
    css: {
        devSourcemap: true,
        preprocessorOptions: {
            scss: {
                silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"]
            },
        }
    },
    optimizeDeps: {
        include: [
            "lodash",
            // the 3 dependencies below are used by ui-libs
            // optimizing them allows storybook to run properly
            // without allowing interop in typescript
            "dayjs",
            "debug",
            "@braintree/sanitize-url",
            "monaco-yaml/yaml.worker",
            "vue-axios",
            "lodash-es",
            "nprogress"
        ],
        exclude: [
            "* > @kestra-io/ui-libs"
        ]
    },
})
