import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

export default defineConfig({
    base: "/ui",
    build: {
        outDir: "../webserver/src/main/resources/ui",
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
        },
    },
    plugins: [
        vue(),
    ],
})
