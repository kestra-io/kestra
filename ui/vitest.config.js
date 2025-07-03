import {defineConfig} from "vite";
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
        projects: [".storybook/vitest.config.js", "./vitest.config.unit.js"],
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})
