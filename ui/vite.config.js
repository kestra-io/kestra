// @ts-check

import {createLogger, defineConfig, mergeConfig} from "vite"
import {federation} from "@module-federation/vite"

/** Silences two harmless node_modules sourcemap warnings: a source outside its package (element-plus) and a missing .map (monaco-editor). */
const logger = createLogger()
/** @param {string} msg */
const isNodeModulesSourcemapWarning = (msg) =>
    (/sourcemap/i).test(msg) && msg.includes("node_modules") && (
        msg.includes("points to a source file outside its package") ||
        msg.includes("An error occurred while trying to read the map file")
    )
const loggerWarn = logger.warn.bind(logger)
/** @param {string} msg
 * @param {any} options */
logger.warn = (msg, options) => {
    if (isNodeModulesSourcemapWarning(msg)) return
    loggerWarn(msg, options)
}
const loggerWarnOnce = logger.warnOnce.bind(logger)
/** @param {string} msg
 * @param {any} options */
logger.warnOnce = (msg, options) => {
    if (isNodeModulesSourcemapWarning(msg)) return
    loggerWarnOnce(msg, options)
}

import {commit} from "./plugins/commit.ts"
import {symlinkAlias} from "./plugins/vite-plugin-symlink-alias.mjs"
import {stripDeadPrebuildDefault} from "./plugins/stripDeadPrebuildDefault.js"
import {consolidateChunks} from "./plugins/consolidateChunks.js"
import {VitePWA} from "vite-plugin-pwa"
import {pwaShortcuts} from "./plugins/pwaShortcuts.ts"
import {baseConfig} from "./vite.config.base.js"

export default defineConfig(({mode}) => {
    return mergeConfig(baseConfig({root: import.meta.dirname, mode, apiProxyPath: "^/api"}), {
        base: "",
        build: {
            outDir: "../webserver/src/main/resources/ui",
            /** Lazy monaco (~4.5 MB) is the largest chunk on purpose; consolidateChunks keeps eager ones lean. */
            chunkSizeWarningLimit: 5000,
            rolldownOptions: {
                /** Route-guard and no-code dynamic imports defer evaluation (boot cost, cycles), not chunking. */
                checks: {ineffectiveDynamicImport: false},
            },
        },
        server: {
            watch: {
                ignored: [
                    "!**/node_modules/@kestra-io/design-system/src/**",
                    "!**/node_modules/@kestra-io/topology/src/**",
                ],
            },
        },
        resolve: {
            preserveSymlinks: true,
        },
        plugins: [
            symlinkAlias(import.meta.dirname),
            !process.env.STORYBOOK && federation({
                name: "host",
                shared: {
                    vue: {
                        singleton: true,
                    },
                },
            }),
            !process.env.STORYBOOK && consolidateChunks(),
            stripDeadPrebuildDefault(),
            commit(),
            !process.env.STORYBOOK && VitePWA({
                /** Registered manually (serviceWorker.ts) so the scope derives from the runtime base path. */
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
                    shortcuts: pwaShortcuts,
                },
                workbox: {
                    /** Shell-only precache: JS and CSS stay network-fetched, the assets/ graph is tens of MB. */
                    globPatterns: ["*.{ico,png,css}"],
                    maximumFileSizeToCacheInBytes: 256 * 1024,
                    /** Disabled: index.html carries a per-request CSRF meta (StaticFilter), so a cached copy breaks CSRF. */
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
        optimizeDeps: {
            entries: [
                "packages/design-system/src/**/*.{ts,vue}",
                "!packages/design-system/src/**/*.test.ts",
            ],
            include: [
                "nprogress",
                /** CJS-only, imported as an ESM default: pre-bundled as ESM so Chromium (storybook) can load it. */
                "vue-gtag",
                /** Lazy-loaded per language by the design system, pre-bundled on the first pass so none triggers a reload at startup.
                 * Dev-server only: the client still receives just the active locale. */
                "dayjs/locale/de",
                "dayjs/locale/es",
                "dayjs/locale/fr",
                "dayjs/locale/hi",
                "dayjs/locale/it",
                "dayjs/locale/ja",
                "dayjs/locale/ko",
                "dayjs/locale/pl",
                "dayjs/locale/pt",
                "dayjs/locale/pt-br",
                "dayjs/locale/ru",
                "dayjs/locale/zh-cn",
                "@module-federation/runtime",
                /** Otherwise discovered late by the federation plugin, which re-optimizes and reloads at startup. */
                "@module-federation/dts-plugin/dynamic-remote-type-hints-plugin",
                "path-browserify",
                /** Dev-only: without it /setup re-optimizes and reloads; the production build keeps its lazy chunk. */
                "mailchecker",
                "rapidoc",
                /** Imported by the AI Copilot: found mid-run, it reloads the dev server and kills whichever storybook test is loading,
                 * which intermittently fails unrelated stories in CI. */
                "@kestra-io/kestra-sdk/ai",
            ],
            exclude: [
                "* > @kestra-io/ui-libs",
                "monaco-yaml",
            ],
        },
    })
})
