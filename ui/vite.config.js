// @ts-check

import path from "path"
import {createLogger, defineConfig, loadEnv} from "vite"
import vue from "@vitejs/plugin-vue"
import {federation} from "@module-federation/vite"

// Silence sourcemap warnings from node_modules that are harmless:
// - "points to a source file outside its package" (element-plus, etc.)
// - missing .map files inside monaco-editor (marked.umd.js.map, etc.)
const logger = createLogger()
/**
 * @param {string} msg 
 * @returns 
 */
const isNodeModulesSourcemapWarning = (msg) =>
    (/sourcemap/i).test(msg) && msg.includes("node_modules") && (
        msg.includes("points to a source file outside its package") ||
        msg.includes("An error occurred while trying to read the map file")
    )
const loggerWarn = logger.warn.bind(logger)
/**
 * @param {string} msg 
 * @param {any} options 
 * @returns 
 */
logger.warn = (msg, options) => {
    if (isNodeModulesSourcemapWarning(msg)) return
    loggerWarn(msg, options)
}
const loggerWarnOnce = logger.warnOnce.bind(logger)
/**
 * @param {string} msg 
 * @param {any} options 
 * @returns 
 */
logger.warnOnce = (msg, options) => {
    if (isNodeModulesSourcemapWarning(msg)) return
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
                        
                    },
                    "@kestra-io/kestra-sdk": {
                        singleton: true,
                    },
                    // add all exports of @kestra-io/kestra-sdk as shared singletons
                    ...Object.fromEntries(Object.keys(kestraSdkExports)
                        .filter((key) => key !== "." && !key.endsWith(".json"))
                        .map((key) => {
                            const name = key.replace(/^\.\//, "").replace(/\/index\.js$/, "")
                            return [`@kestra-io/kestra-sdk/${name}`, {
                                singleton: true,
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
                "path-browserify",
            ],
            exclude: [
                "* > @kestra-io/ui-libs",
                "@kestra-io/design-system",
                "@kestra-io/topology",
                "monaco-editor",
                "monaco-yaml",
                "monaco-worker-manager",
                "monaco-marker-data-provider",
            ],
        },
    }
})
