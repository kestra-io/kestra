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

// `--merge-reports` only recombines existing report blobs; it runs no tests.
// But loading the config still boots a Vite dev server whose dependency scan
// segfaults (CI and locally). We can't swap in a lighter project to dodge it:
// the merge recreates each spec using the pool recorded in its blob ("browser"),
// so the project's pool shape must match the sharded runs exactly.
// Instead, disable the scan during merge only — nothing is served here anyway.
const isMergeReports = process.argv.includes("--merge-reports")

const resolvedViteConfig = typeof viteConfig === "function" ? viteConfig({mode: "test"}) : viteConfig

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
            mergeConfig(resolvedViteConfig, {
                plugins: [
                    // The plugin will run tests for the stories defined in your Storybook config
                    // See options at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon#storybooktest
                    storybookTest({
                        configDir: path.join(dirname, ".storybook"),
                    }),
                ],
                // Merge-only: skip Vite's automatic dependency scan — see the
                // comment above `isMergeReports` for why.
                ...(isMergeReports ? {optimizeDeps: {noDiscovery: true, entries: []}} : {}),
                test: {
                    name: "storybook",
                    setupFiles: ["./.storybook/vitest.setup.js"],
                    // Only takes effect during the `--merge-reports` replay (see
                    // run-storybook-tests.sh): the sharded runs pass their own
                    // `--reporter` flags on the CLI, which take precedence over this
                    // config. The merge step doesn't, so this is what produces the
                    // single, whole-suite JUnit report CI reads to list failing tests.
                    reporters: [
                        ["default"],
                        ["junit"],
                    ],
                    outputFile: {
                        junit: "./test-report.storybook.junit.xml",
                    },
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
