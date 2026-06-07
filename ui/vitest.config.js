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
    coverage: {
        exclude: ["**/*.json"],
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
                test: {
                    name: "storybook",
                    setupFiles: ["./.storybook/vitest.setup.js"],
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
                    coverage: {
                        reporter: ["text", "html"],
                        exclude: [
                            "**/*.stories.{ts,tsx}",
                            "**/*.spec.{ts,tsx}",
                            "**/node_modules/**",
                            "**/*.json",
                        ],
                    },
                },
            }),
        ],
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
