import {coverageConfigDefaults, defineProject} from "vitest/config";
import vue from "@vitejs/plugin-vue";

import viteConfig from "./vite.config.js";

export default defineProject({
    plugins: [
        vue(),
    ],
    resolve: {
        alias: {
            ...viteConfig.resolve.alias,
        },
    },
    test: {
        name: "unit",
        environment: "jsdom",
        reporters: [
            ["default"],
            ["junit"]
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
                ...coverageConfigDefaults.exclude,
                "stylelint.config.mjs",
                "storybook-static/**",
                "**/.storybook/**",
                "**/*.stories.*",
                "**/*.d.ts",
            ]
        },
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
