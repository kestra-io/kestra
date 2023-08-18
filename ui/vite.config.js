import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import pluginRewriteAll from 'vite-plugin-rewrite-all';

export default defineConfig({
    base: "",
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
        pluginRewriteAll()
    ],
    css: {
        devSourcemap: true
    },
})
