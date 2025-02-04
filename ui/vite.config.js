import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

import {filename} from "./plugins/filename"
import {commit} from "./plugins/commit"

export const manualChunks = {
    // bundle dashboard and all its dependencies in a single chunk
    "dashboard": [
        "src/components/dashboard/Dashboard.vue", 
        "src/components/dashboard/components/DashboardCreate.vue", 
        "src/override/components/dashboard/components/DashboardEdit.vue"
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
        },
    },
    resolve: {
        alias: {
            "override": path.resolve(__dirname, "src/override/"),
            "#imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#build/mdc-image-component.mjs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-imports": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "#mdc-configs": path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js"),
            "shiki": path.resolve(__dirname, "node_modules/shiki/dist"),
            "vuex": path.resolve(__dirname, "node_modules/vuex/dist/vuex.esm-bundler.js"),
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
            "lodash",
            // the 3 dependencies below are used by ui-libs
            // optimizing them allows storybook to run properly
            // without allowing interop in typescript
            "dayjs",
            "debug",
            "@braintree/sanitize-url"
        ],
        exclude: [
            "* > @kestra-io/ui-libs"
        ]
    },
})
