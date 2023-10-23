import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import pluginRewriteAll from 'vite-plugin-rewrite-all';
import {visualizer} from "rollup-plugin-visualizer";
import copy from 'rollup-plugin-copy'

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
        copy({
            hook: 'buildStart',
            targets: [
                {
                    src: 'node_modules/vscode-web/dist/out/*',
                    dest: 'public/vscode-web/dist/out/'
                },
                {
                    src: 'node_modules/vscode-web/*',
                    dest: 'public/vscode-web/'
                }
            ]
        }),
        vue(),
        pluginRewriteAll(),
        visualizer(),
    ],
    css: {
        devSourcemap: true
    },
    optimizeDeps: {
        include: [
            "lodash"
        ],
        exclude: [
            '* > @kestra-io/ui-libs'
        ]
    },
})
