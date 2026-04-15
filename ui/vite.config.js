import path from "path";
import {defineConfig, loadEnv} from "vite";
import vue from "@vitejs/plugin-vue";

import {commit} from "./plugins/commit"
import {codecovVitePlugin} from "@codecov/vite-plugin";

export default defineConfig(({mode}) => {
    process.env = {...process.env, ...loadEnv(mode, process.cwd())};

    return {
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
                        ],
                    }
                }
            }
        },
        server: {
            proxy: {
                "^/api": {
                    target: process.env.VITE_APP_LOGIN_URL || "http://localhost:8080",
                    ws: true,
                    changeOrigin: true
                }
            },
            fs: {
                allow: [".", "../ui-design-system"]
            }
        },
        resolve: {
            preserveSymlinks: true,
            dedupe: ["echarts", "vue-echarts"],
            alias: [
                {find: /^@kestra-io\/ui-design-system$/, replacement: path.join(__dirname, "node_modules/@kestra-io/ui-design-system/src/index.ts")},
                {find: /^@kestra-io\/ui-design-system\/(.*)$/, replacement: path.resolve(__dirname, "node_modules/@kestra-io/ui-design-system") + "/$1"},
                {find: "override", replacement: path.resolve(__dirname, "src/override/")},
                {find: "kestra-api", replacement: path.resolve(__dirname, "src/generated/kestra-api/")},
                {find: "@storybook/addon-actions", replacement: "storybook/actions"},

                // to be removed when all mdc import are removed
                // Rolldown failed to resolve import "#imports" from "kestra/ui/node_modules/@nuxtjs/mdc/dist/runtime/components/prose/ProseH3.vue".
                {find: "#imports", replacement: path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js")},
                {find: "#build/mdc-image-component.mjs", replacement: path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js")},
                {find: "#mdc-imports", replacement: path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js")},
                {find: "#mdc-configs", replacement: path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js")},
            ],
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
                    silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"],
                    loadPaths: [path.resolve(__dirname, "node_modules")]
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
                "lodash-es",
                "nprogress",
                // CJS-only packages imported as ESM defaults by unified, fault, @kestra-io/ui-libs, etc.
                // Adding them here makes Vite pre-bundle them as ESM so Chromium (storybook) can load them.
                "extend",
                "format",
                "humanize-duration",
                "moment",
                "moment-timezone",
                "moment-range"
            ],
            exclude: [
                "* > @kestra-io/ui-libs",
                "@kestra-io/ui-design-system"
            ]
        },
    };
});
