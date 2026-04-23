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
            dedupe: ["echarts", "vue-echarts", "dayjs", "vue", "vue-router", "vue-i18n", "@vueuse/core", "pinia"],
            alias: [
                {find: /^echarts(.*)$/, replacement: path.resolve(__dirname, "node_modules/echarts") + "$1"},
                {find: /^vue-echarts$/, replacement: path.resolve(__dirname, "node_modules/vue-echarts")},
                {find: /^element-plus(.*)$/, replacement: path.resolve(__dirname, "node_modules/element-plus") + "$1"},
                {find: /^vue$/, replacement: path.resolve(__dirname, "node_modules/vue")},
                {find: /^vue-router$/, replacement: path.resolve(__dirname, "node_modules/vue-router")},
                {find: /^vue-i18n$/, replacement: path.resolve(__dirname, "node_modules/vue-i18n")},
                {find: /^@vueuse\/core$/, replacement: path.resolve(__dirname, "node_modules/@vueuse/core")},
                {find: /^moment$/, replacement: path.resolve(__dirname, "node_modules/moment")},
                {find: /^moment-timezone$/, replacement: path.resolve(__dirname, "node_modules/moment-timezone")},
                {find: /^yaml$/, replacement: path.resolve(__dirname, "node_modules/yaml")},
                {find: /^bootstrap$/, replacement: path.resolve(__dirname, "node_modules/bootstrap")},
                {find: /^humanize-duration$/, replacement: path.resolve(__dirname, "node_modules/humanize-duration")},
                {find: /^js-yaml$/, replacement: path.resolve(__dirname, "node_modules/js-yaml")},
                {find: /^mermaid$/, replacement: path.resolve(__dirname, "node_modules/mermaid")},
                {find: /^unified$/, replacement: path.resolve(__dirname, "node_modules/unified")},
                {find: /^remark-parse$/, replacement: path.resolve(__dirname, "node_modules/remark-parse")},
                {find: /^remark-gfm$/, replacement: path.resolve(__dirname, "node_modules/remark-gfm")},
                {find: /^remark-directive$/, replacement: path.resolve(__dirname, "node_modules/remark-directive")},
                {find: /^remark-frontmatter$/, replacement: path.resolve(__dirname, "node_modules/remark-frontmatter")},
                {find: /^xss$/, replacement: path.resolve(__dirname, "node_modules/xss")},
                {find: /^shiki\/langs\/(.*)$/, replacement: path.resolve(__dirname, "node_modules/shiki/dist/langs") + "/$1"},
                {find: /^shiki\/themes\/(.*)$/, replacement: path.resolve(__dirname, "node_modules/shiki/dist/themes") + "/$1"},
                {find: /^shiki\/engine\/javascript$/, replacement: path.resolve(__dirname, "node_modules/shiki/dist/engine-javascript.mjs")},
                {find: /^shiki\/engine\/oniguruma$/, replacement: path.resolve(__dirname, "node_modules/shiki/dist/engine-oniguruma.mjs")},
                {find: /^shiki$/, replacement: path.resolve(__dirname, "node_modules/shiki/dist/index.mjs")},
                {find: /^vue-material-design-icons\/(.*)$/, replacement: path.resolve(__dirname, "node_modules/vue-material-design-icons") + "/$1"},
                {find: /^@kestra-io\/ui-design-system$/, replacement: path.resolve(__dirname, "../ui-design-system/src/index.ts")},
                {find: /^@kestra-io\/ui-design-system\/(.*)$/, replacement: path.resolve(__dirname, "../ui-design-system") + "/$1"},
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
                "element-plus",
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
