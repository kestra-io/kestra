import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import pluginRewriteAll from 'vite-plugin-rewrite-all';
import {visualizer} from "rollup-plugin-visualizer";

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
        pluginRewriteAll(),
        visualizer()
    ],
    css: {
        devSourcemap: true
    },
    optimizeDeps: {
        exclude: [
            '* > @kestra-io/ui-libs'
        ]
    },
})
