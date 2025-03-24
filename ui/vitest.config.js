import {defineConfig} from "vite"
import vue from "@vitejs/plugin-vue"
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
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
})