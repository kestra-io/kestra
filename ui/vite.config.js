import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import pluginRewriteAll from "vite-plugin-rewrite-all";
import {visualizer} from "rollup-plugin-visualizer";
import eslintPlugin from "vite-plugin-eslint";

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            "assets": path.resolve(__dirname, "src/assets/"),
            "utils": path.resolve(__dirname, "src/utils/"),
        },
    },
    plugins: [
        vue(),
        pluginRewriteAll(),
        visualizer(),
        eslintPlugin({
            failOnWarning: true,
            failOnError: true
        })
    ],
    assetsInclude: ["**/*.md"],
    css: {
        devSourcemap: true
    },
    optimizeDeps: {
        include: [
            "lodash"
        ],
        exclude: [
            "* > @kestra-io/ui-libs"
        ]
    },
})
