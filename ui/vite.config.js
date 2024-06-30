import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import {visualizer} from "rollup-plugin-visualizer";
import eslintPlugin from "vite-plugin-eslint";

export default defineConfig({
    base: "",
    define: {
        "import.meta.env.__ROOT_DIR__": JSON.stringify(__dirname),
    },
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
