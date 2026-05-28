import {defineConfig} from "vitest/config"
import vue from "@vitejs/plugin-vue"
import {storybookTest} from "@storybook/addon-vitest/vitest-plugin"
import {resolve} from "path"
import {playwright} from "@vitest/browser-playwright"

// @vue/compiler-dom passes a browser-only `decodeEntities` option to
// @vue/compiler-core during Vite's Node.js transform phase.  The core
// compiler warns that the option is ignored in non-browser builds — this
// is a known false-positive that produces no functional difference.
// Suppress it so test output stays clean.
const consoleWarnBak = console.warn.bind(console)
console.warn = (...args: unknown[]) => {
    if (typeof args[0] === "string" && args[0].includes("decodeEntities")) return
    consoleWarnBak(...args)
}

const storybookConfigDir = resolve(import.meta.dirname, ".storybook")
const storybookPlugins = await storybookTest({configDir: storybookConfigDir})

export default defineConfig({
    test: {
        projects: [
            // ── Browser project ──────────────────────────────────────────────────────
            // Runs every story as a test (play function = test body).
            // This is the project the Storybook UI "Tests" panel connects to.
            // Name must match the filter sent by the addon: "storybook:<abs-path>/.storybook"
            {
                plugins: [vue(), ...storybookPlugins],
                // Replacing import.meta.env with a static object prevents coverage
                // instrumentation from doing dynamic property access (e.g. [Symbol.toStringTag])
                // which the browser module runner does not support.
                define: {
                    "import.meta.env": JSON.stringify({
                        PROD: false,
                        DEV: true,
                        SSR: false,
                        BASE_URL: "/",
                        MODE: "test",
                    }),
                },
                test: {
                    name: `storybook:${storybookConfigDir}`,
                    browser: {
                        enabled: true,
                        headless: true,
                        provider: playwright(),
                        instances: [{browser: "chromium"}],
                    },
                    setupFiles: ["./.storybook/vitest.setup.ts"],
                },
            },

            // ── Unit project ─────────────────────────────────────────────────────────
            // Mounts KsSelect/KsOption directly with @vue/test-utils + jsdom.
            // Fast, no browser needed, no Storybook runtime dependency.
            {
                plugins: [vue()],
                test: {
                    name: "unit",
                    environment: "jsdom",
                    globals: true,
                    browser: {enabled: false},
                    include: ["tests/**/*.test.ts"],
                },
            },
        ],
    },
})
