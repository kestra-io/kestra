import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

import {filename} from "./plugins/filename"
import {commit} from "./plugins/commit"
import {codecovVitePlugin} from "@codecov/vite-plugin";

export const manualChunks = {
    // bundle dashboard and all its dependencies in a single chunk
    "dashboard": [
        "src/components/dashboard/Dashboard.vue",
        "src/components/dashboard/components/Create.vue",
        "src/override/components/dashboard/Edit.vue"
    ],
    // bundle flows and all its dependencies in a second chunk
    "flows": [
        "src/components/flows/Flows.vue",
        "src/components/flows/FlowCreate.vue",
        "src/components/flows/FlowsSearch.vue",
        "src/components/flows/FlowRoot.vue"
    ],
    "markdownDeps": [
        "shiki/langs/yaml.mjs",
        "shiki/langs/python.mjs",
        "shiki/langs/javascript.mjs",
        "src/utils/markdownDeps.ts"
    ]
}

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
        rollupOptions: {
            output: {
                manualChunks
            }
        }
    },
    server: {
        proxy: {
            "^/api": {
                target: "http://kestra:8080", // Make sure to change your /etc/hosts file, to contain this line: 127.0.0.1 kestra
                ws: true,
                changeOrigin: true
            }
        }
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            "#imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#build/mdc-image-component.mjs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-configs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "vuex": path.resolve(__dirname, "node_modules/vuex/dist/vuex.esm-bundler.js"),
            "@storybook/addon-actions": "storybook/actions",
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
        filename(),
        commit(),
        codecovVitePlugin({
            enableBundleAnalysis: process.env.CODECOV_TOKEN !== undefined,
            bundleName: "ui",
            uploadToken: process.env.CODECOV_TOKEN,
            telemetry: false
        }),
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
            "lodash",
            // the 3 dependencies below are used by ui-libs
            // optimizing them allows storybook to run properly
            // without allowing interop in typescript
            "dayjs",
            "debug",
            "@braintree/sanitize-url",
            "monaco-yaml/yaml.worker",
            "vue-axios",
            "lodash-es",
            "nprogress"
        ],
        exclude: [
            "* > @kestra-io/ui-libs"
        ]
    },
})
