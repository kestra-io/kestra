import {defineConfig} from "vite";
import {coverageConfigDefaults} from "vitest/config";
import vue from "@vitejs/plugin-vue";
import path from "path";

export default defineConfig({
    plugins: [
        vue(),
    ],
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
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
        }
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
