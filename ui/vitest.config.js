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
            exclude: [
                ...coverageConfigDefaults.exclude,
                "stylelint.config.mjs",
                "storybook-static/**",
            ]
        }
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})