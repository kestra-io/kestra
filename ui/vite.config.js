import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";
import {visualizer} from "rollup-plugin-visualizer";

import {filename} from "./plugins/filename"
import {commit} from "./plugins/commit"

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            // allow to render at runtime
            vue: "vue/dist/vue.esm-bundler.js",

            "#imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#build/mdc-image-component.mjs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-configs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "shiki": path.resolve(__dirname, "node_modules/shiki/dist"),
        },
    },
    plugins: [
        vue({
            template: {
                compilerOptions: {
                    isCustomElement: (tag) => {
                        return tag === "rapi-doc";
                    }
                }
            }
        }),
        visualizer(),
        filename(),
        commit()
    ],
    assetsInclude: ["**/*.md"],
    css: {
        devSourcemap: true,
        preprocessorOptions: {
            scss: {
                silenceDeprecations: ["mixed-decls", "color-functions", "global-builtin", "import"]
            },
        }
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
