import {defineProject} from "vitest/config"
import vue from "@vitejs/plugin-vue"

import viteConfig from "./vite.config.js"

const resolvedViteConfig = typeof viteConfig === "function" ? viteConfig({mode: "test"}) : viteConfig

export default defineProject({
    plugins: [
        vue(),
    ],
    resolve: resolvedViteConfig.resolve,
    test: {
        name: "unit",
        environment: "jsdom",
        setupFiles: ["./tests/unit/setup.ts"],
        reporters: [
            ["default"],
            ["junit"],
        ],
        outputFile: {
            junit: "./test-report.junit.xml",
        },
        exclude: [
            "tests/e2e/**",
            // Match node_modules at ANY depth. A bare "node_modules/**" only excludes
            // the top-level one, so bundled test files inside nested package
            // node_modules (e.g. packages/topology/node_modules/cytoscape/**,
            // @upsetjs/venn.js, ts-dedent) leaked into this project and failed to
            // collect (they expect jest/playwright globals, not vitest).
            "**/node_modules/**",
            "tests/unit/**/translation.spec.js",
            // Design system runs in its own CI job with its own config/setup; no more double run.
            "packages/design-system/**",
        ],
        server: {
            deps: {
                // element-plus components do `import { placements } from "@popperjs/core"`;
                // externalised in jsdom that resolves popper's CJS build → "Named export
                // 'placements' not found ... is a CommonJS module". Inlining element-plus (and
                // popper) routes them through Vite's transform, which provides the named
                // exports. This is what unblocks the ~20 element-plus-backed suites.
                inline: [/element-plus/, "@popperjs/core"],
            },
        },
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
