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
            "override/services/filterLanguagesProvider": path.resolve(__dirname, "tests/storybook/mocks/services/filterLanguagesProvider.mock.ts"),
            "override": path.resolve(__dirname, "src/override/"),
            "#imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#build/mdc-image-component.mjs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-configs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "shiki": path.resolve(__dirname, "node_modules/shiki/dist"),
            "vuex": path.resolve(__dirname, "node_modules/vuex/dist/vuex.esm-bundler.js"),
            "@storybook/addon-actions": "storybook/actions"
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
        },
        projects: [".storybook/vitest.config.js"]
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
