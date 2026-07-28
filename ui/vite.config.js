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
import {symlinkAlias} from "./plugins/vite-plugin-symlink-alias.mjs"
import {codecovVitePlugin} from "@codecov/vite-plugin"
import {stripDeadPrebuildDefault} from "./plugins/stripDeadPrebuildDefault.js"
import {VitePWA} from "vite-plugin-pwa"
import {loaderFragment} from "./plugins/loaderFragment.js"

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
                // Lets @kestra-io/kestra-sdk's dev-only staleness check reach the backend's served
                // OpenAPI spec (${context-path}/swagger/kestra.yml) to compare its hash. Dev-only;
                // the check itself is tree-shaken from production builds.
                "^/swagger": {
                    target: process.env.VITE_APP_LOGIN_URL || "http://localhost:8080",
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
            symlinkAlias(__dirname),
            loaderFragment(),
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
            stripDeadPrebuildDefault(),
            commit(),
            codecovVitePlugin({
                enableBundleAnalysis: process.env.CODECOV_TOKEN !== undefined,
                bundleName: "ui",
                uploadToken: process.env.CODECOV_TOKEN,
                telemetry: false,
            }),
            !process.env.STORYBOOK && VitePWA({
                // registered manually (serviceWorker.ts) so scope derives from the runtime base path
                injectRegister: null,
                manifestFilename: "manifest.webmanifest",
                includeManifestIcons: false,
                manifest: {
                    name: "Kestra",
                    short_name: "Kestra",
                    description: "Kestra - Declarative Data Orchestration Platform",
                    start_url: "./",
                    scope: "./",
                    display: "standalone",
                    theme_color: "#631bf3",
                    background_color: "#ffffff",
                    icons: [
                        {src: "pwa-192x192.png", sizes: "192x192", type: "image/png", purpose: "any"},
                        {src: "pwa-512x512.png", sizes: "512x512", type: "image/png", purpose: "any"},
                        {src: "maskable-icon-512x512.png", sizes: "512x512", type: "image/png", purpose: "maskable"},
                    ],
                },
                workbox: {
                    // shell-only precache: JS/CSS stays network-fetched (the assets/ graph is tens of MB)
                    globPatterns: ["*.{ico,png,css}"],
                    maximumFileSizeToCacheInBytes: 256 * 1024,
                    // disabled: index.html carries a per-request CSRF meta (StaticFilter); a cached copy breaks CSRF
                    navigateFallback: undefined,
                    skipWaiting: true,
                    clientsClaim: true,
                    runtimeCaching: [
                        {
                            urlPattern: /\/api\//,
                            handler: "NetworkOnly",
                        },
                    ],
                },
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
                "mailchecker",
                "rapidoc",
                // The AI Copilot stories/components import the SDK's `ai` subpath. Pre-bundle it so
                // Vite doesn't discover it mid-run and reload the dev server — that reload kills
                // whichever storybook test is loading at that instant (the addon-vitest setup import
                // then fails), which is what intermittently red-flags unrelated stories in CI.
                "@kestra-io/kestra-sdk/ai",
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
