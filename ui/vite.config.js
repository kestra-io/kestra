import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import {visualizer} from "rollup-plugin-visualizer";
import eslintPlugin from "vite-plugin-eslint";

import {filename} from "./plugins/filename"
import {details} from "./plugins/details"

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            // allow to render at runtime
            vue: "vue/dist/vue.esm-bundler.js"
        },
    },
    plugins: [
        vue(),
        visualizer(),
        eslintPlugin({
            failOnWarning: true,
            failOnError: true
        }),
        filename(),
        details()
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
