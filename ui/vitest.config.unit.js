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
        reporters: [
            ["default"],
            ["junit"],
        ],
        outputFile: {
            junit: "./test-report.junit.xml",
        },
        exclude: [
            "tests/e2e/**",
            "node_modules/**",
            "tests/unit/**/translation.spec.js",
        ],
        coverage: {
            include: [
                "src/**/*.{js,ts,vue}",
            ],
            exclude: [
                "stylelint.config.mjs",
                "storybook-static/**",
                "**/.storybook/**",
                "**/*.stories.*",
                "**/*.d.ts",
            ],
        },
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
