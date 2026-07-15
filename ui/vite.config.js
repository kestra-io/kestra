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
            symlinkAlias(__dirname),
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
                // Registered manually at runtime (see src/utils/serviceWorker.ts) so the
                // scope can be computed from window.KESTRA_BASE_PATH: one build is shipped
                // for every deploy path (root, sub-path, behind a proxy), so the scope can't
                // be baked in at build time.
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
                    // Every JS/CSS chunk (entry included) lives under assets/ and, because
                    // Monaco, shiki (one grammar/theme chunk per language), mermaid, katex,
                    // echarts and the topology graph are all reachable from the entry, this
                    // app has no small, cleanly-separable "critical entry chunk" - the whole
                    // assets/ graph is tens of MB. So the app shell we precache is just the
                    // static root-level files (manifest, icons, loader stylesheet); JS/CSS is
                    // always fetched from the network, keeping the first install tiny.
                    // manifest.webmanifest is precached automatically by the plugin itself;
                    // matching it here too would just add a harmless duplicate entry.
                    globPatterns: ["*.{ico,png,css}"],
                    maximumFileSizeToCacheInBytes: 256 * 1024,
                    // vite-plugin-pwa defaults navigateFallback to "index.html", which would
                    // register a NavigationRoute bound to a precache entry we don't generate
                    // (see above) and break every navigation once the SW is active. Disabled:
                    // the webserver injects a fresh, request-scoped CSRF meta tag into
                    // index.html on every request (StaticFilter); serving a precached/fallback
                    // copy for navigations would ship a stale/missing token and break the next
                    // mutating API call. Navigation requests always hit the network.
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
