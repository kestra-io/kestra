import {defineProject} from "vitest/config";
import vue from "@vitejs/plugin-vue";
import path from "path";

import viteConfig from "./vite.config.js";

export default defineProject({
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
        name: "language",
        environment: "node",
        include: [
            "tests/unit/**/translation.spec.js"
        ]
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
