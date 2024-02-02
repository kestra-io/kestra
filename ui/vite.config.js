import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import pluginRewriteAll from "vite-plugin-rewrite-all";
import {visualizer} from "rollup-plugin-visualizer";
import copy from "rollup-plugin-copy"
import downloadVsix from "./download-vsix-rollup-plugin"

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
            hook: "buildStart",
            targets: [
                {
                    src: "node_modules/vscode-web/dist/out/*",
                    dest: "public/vscode-web/dist/out/"
                },
                {
                    src: "node_modules/vscode-web/*",
                    dest: "public/vscode-web/"
                }
            ]
        }),
        downloadVsix({
            targets: [
                // increase build by 80mb
                // {
                //     vsixUrl: "https://github.com/kestra-io/vscode-kestra/releases/download/v0.1.7/ms-python.vscode-pylance-2023.11.12.vsix",
                //     outputDir: "public/vscode/extensions/pylance"
                // },
                {
                    vsixUrl: "https://github.com/kestra-io/vscode-kestra/releases/download/v0.1.7/vscode-yaml-1.14.1.vsix",
                    outputDir: "public/vscode/extensions/yaml"
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
            "* > @kestra-io/ui-libs"
        ]
    },
})
