import {defineConfig} from "vite";
import {coverageConfigDefaults} from "vitest/config";
import vue from "@vitejs/plugin-vue";
import path from "path";

import viteConfig from "./vite.config.js";

export default defineConfig({
    plugins: [
        vue(),
    ],
    resolve: {
        alias: {
            "override/services/filterLanguagesProvider": path.resolve(__dirname, "tests/storybook/mocks/services/filterLanguagesProvider.mock.ts"),
            ...viteConfig.resolve.alias,
        },
    },
    test: {
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
        projects: [".storybook/vitest.config.js"]
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
