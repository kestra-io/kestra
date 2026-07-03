import {defineConfig} from "vite"
import vue from "@vitejs/plugin-vue"

import {mergeConfig} from "vitest/config"
import viteConfig from "./vite.config.js"
import path from "node:path"
import {fileURLToPath} from "node:url"
import {storybookTest} from "@storybook/addon-vitest/vitest-plugin"
import {playwright} from "@vitest/browser-playwright"

const dirname =
    typeof __dirname !== "undefined"
        ? __dirname
        : path.dirname(fileURLToPath(import.meta.url))

// `vitest run --merge-reports` never executes tests — it only reads the blob
// files written by the sharded runs and regenerates a combined report. But
// merely loading the full "storybook" project config still boots a Vite dev
// server and its esbuild dependency optimizer for the browser/storybookTest
// plugin stack, which reproducibly segfaults in this environment (confirmed
// both in CI and locally). Toggling `browser.enabled` off doesn't avoid this —
// it still loads the same heavy plugin stack — and additionally makes Vitest's
// spec resolver drop all browser-pool files, which then fails the merge with
// "No test files found". Skip the heavy project definition entirely during
// merge and swap in a bare-bones one that only carries the matching name, which
// is all `--merge-reports` needs to attribute blob data back to this project.
// It still needs its own `include` matching real files on disk though — even
// in merge mode, Vitest validates every project has at least one discoverable
// file before proceeding, and the default `**/*.{test,spec}...` glob matches
// none of the `*.stories.*` files here, which reproduces the same
// "No test files found" failure via a different path.
const isMergeReports = process.argv.includes("--merge-reports")

const resolvedViteConfig = typeof viteConfig === "function" ? viteConfig({mode: "test"}) : viteConfig

// No backend is available during tests — clear the API proxy so Vite doesn't
// emit "[vite] http proxy error" for every story that fires an /api request.
if (resolvedViteConfig.server) {
    resolvedViteConfig.server.proxy = {}
}

// @vue/compiler-dom passes a browser-only `decodeEntities` option to
// @vue/compiler-core during Vite's Node.js transform phase. The core
// compiler warns that the option is ignored in non-browser builds — this
// is a known false-positive that produces no functional difference.
// Suppress it so test output stays clean.
const originalConsoleWarn = console.warn.bind(console)
console.warn = (...args) => {
    if (typeof args[0] === "string" && args[0].includes("decodeEntities")) return
    originalConsoleWarn(...args)
}

// Vite writes logger warnings to process.stderr. Silence the
// "Sourcemap for X points to a source file outside its package" noise
// emitted when node_modules packages reference scss from sibling packages
// (e.g. element-plus inside design-system, design-system inside topology).
// These are harmless cross-package sourcemap references that flood test output.
const isElementPlusSourcemapWarning = (s) =>
    /sourcemap/i.test(s) && s.includes("points to a source file outside its package") && s.includes("node_modules")
const origStderrWrite = process.stderr.write.bind(process.stderr)
process.stderr.write = (chunk, ...rest) => {
    if (typeof chunk === "string" && isElementPlusSourcemapWarning(chunk)) return true
    return origStderrWrite(chunk, ...rest)
}
const origStdoutWrite = process.stdout.write.bind(process.stdout)
process.stdout.write = (chunk, ...rest) => {
    if (typeof chunk === "string" && isElementPlusSourcemapWarning(chunk)) return true
    return origStdoutWrite(chunk, ...rest)
}

// More info at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon
export default defineConfig({
    plugins: [vue()],
    resolve: {
        ...resolvedViteConfig.resolve,
        alias: [
            ...resolvedViteConfig.resolve.alias,
        ],
    },
    test: {
        projects: [
            "./vitest.config.unit.js",
            isMergeReports
                ? {
                    test: {
                        name: "storybook",
                        include: ["tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
                        exclude: ["**/*.mdx"],
                    },
                }
                : mergeConfig(resolvedViteConfig, {
                    plugins: [
                        // The plugin will run tests for the stories defined in your Storybook config
                        // See options at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon#storybooktest
                        storybookTest({
                            configDir: path.join(dirname, ".storybook"),
                        }),
                    ],
                    test: {
                        name: "storybook",
                        setupFiles: ["./.storybook/vitest.setup.js"],
                        // Each worker drives its own headless Chromium instance; letting
                        // this scale with CPU count (the default) spins up enough
                        // concurrent browsers to exhaust CI memory, which kills a
                        // worker mid-run and surfaces as "[birpc] rpc is closed,
                        // cannot call 'createTesters'" rather than a real test failure.
                        maxWorkers: 2,
                        browser: {
                            enabled: true,
                            headless: true,
                            provider: playwright(),
                            instances: [
                                {
                                    browser: "chromium",
                                },
                            ],
                        },
                    },
                }),
        ],
        coverage: {
            reporter: ["text", "html"],
            include: [
                "src/**/*.{ts,vue}",
            ],
            exclude: [
                "**/node_modules/**",
                "**/*.stories.*",
                "**/*.spec.{ts,tsx}",
                "**/*.d.ts",
                "**/.storybook/**",
                "storybook-static/**",
                "stylelint.config.mjs",
            ],
        },
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
