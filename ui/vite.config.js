import path from "path"
import {createLogger, defineConfig, loadEnv} from "vite"
import vue from "@vitejs/plugin-vue"
import {federation} from "@module-federation/vite"

// silence some scss warnings about sourceMaps of
// element-plus/theme-chalk/src in the wrong directory
// and will not be published in prod builds
const logger = createLogger()
const loggerWarnOnce = logger.warnOnce.bind(logger)
logger.warnOnce = (msg, options) => {
    if (msg.includes("node_modules/element-plus/theme-chalk/src") && (/sourcemap/i).test(msg)) return
    loggerWarnOnce(msg, options)
}

import {commit} from "./plugins/commit"
import {codecovVitePlugin} from "@codecov/vite-plugin"

import {exports as kestraSdkExports} from "@kestra-io/kestra-sdk/package.json"

export default defineConfig(({mode}) => {
    process.env = {...process.env, ...loadEnv(mode, process.cwd())}

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
                    },
                },
            },
        },
        server: {
            watch: {
                ignored: [
                    "!**/node_modules/@kestra-io/design-system/src/**",
                    "!**/node_modules/@kestra-io/topology/src/**",
                ],
            },
            proxy: {
                "^/api": {
                    target: process.env.VITE_APP_LOGIN_URL || "http://localhost:8080",
                    ws: true,
                    changeOrigin: true,
                },
            },
        },
        resolve: {
            preserveSymlinks: true,
            dedupe: ["echarts", "vue-echarts", "dayjs", "vue", "vue-router", "vue-i18n", "@vueuse/core", "pinia", "@vue-flow/core", "@vue-flow/background", "@vue-flow/controls"],
            alias: [
                {find: "override", replacement: path.resolve(__dirname, "src/override/")},
                {find: "kestra-api", replacement: path.resolve(__dirname, "src/generated/kestra-api/")},

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
                            return tag === "rapi-doc"
                        },
                    },
                },
            }),
            !process.env.STORYBOOK && federation({
                name: "host",
                shared: {
                    vue: {
                        singleton: true,
                        eager: true,
                        requiredVersion: "^3",
                    },
                    "@kestra-io/kestra-sdk": {
                        singleton: true,
                        eager: true,
                    },
                    // add all exports of @kestra-io/kestra-sdk as shared singletons
                    ...Object.fromEntries(Object.keys(kestraSdkExports)
                        .filter((key) => key !== ".")
                        .map((key) => {
                            const name = key.replace(/^\.\//, "").replace(/\/index\.js$/, "")
                            return [`@kestra-io/kestra-sdk/${name}`, {
                                singleton: true,
                                eager: true,
                            }]
                        }),
                    ),
                },
            }),
            commit(),
            codecovVitePlugin({
                enableBundleAnalysis: process.env.CODECOV_TOKEN !== undefined,
                bundleName: "ui",
                uploadToken: process.env.CODECOV_TOKEN,
                telemetry: false,
            }),
        ],
        assetsInclude: ["**/*.md"],
        customLogger: logger,
        css: {
            devSourcemap: true,
            preprocessorOptions: {
                scss: {
                    silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"],
                    loadPaths: [path.resolve(__dirname, "node_modules")],
                },
            },
        },
        optimizeDeps: {
            entries: [
                "tests/storybook/**/*.stories.{js,jsx,ts,tsx}",
                "packages/design-system/src/**/*.{ts,vue}",
                "node_modules/@kestra-io/design-system/src/**/*.{ts,vue}",
            ],
            include: [
                "lodash",
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
                "moment-range",
                "dagre",
                "@vue-flow/background",
                "@vue-flow/controls",
                "html-to-image",
                "@module-federation/runtime",
                "js-yaml",
            ],
            exclude: [
                "* > @kestra-io/ui-libs",
                "@kestra-io/design-system",
                "@kestra-io/topology",
            ],
        },
    }
})
