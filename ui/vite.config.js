import path from "path";
import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

import {commit} from "./plugins/commit"
import {codecovVitePlugin} from "@codecov/vite-plugin";

const MDC_STUB = path.resolve(__dirname, "node_modules/@kestra-io/ui-libs/stub-mdc-imports.js");
const NUXTJS_MDC_STUB = path.resolve(__dirname, "plugins/stub-nuxtjs-mdc.js");

export default defineConfig({
    base: "",
    build: {
        outDir: "../webserver/src/main/resources/ui",
        rollupOptions: {
            output: {
                codeSplitting: {
                    groups: [
                        {
                            test: /src\/components\/dashboard/i,
                            name: "dashboard",
                        },
                        {
                            test: /src\/components\/flows/i,
                            name: "flows",
                        },
                        {
                            test: /(shiki\/langs)|(src\/utils\/markdownDeps)/,
                            name: "markdownDeps",
                        },
                    ],
                }
            }
        }
    },
    server: {
        proxy: {
            "^/api": {
                target: "http://localhost:8080",
                ws: true,
                changeOrigin: true
            }
        }
    },
    resolve: {
        alias: [
            {find: "override", replacement: path.resolve(__dirname, "src/override/")},
            {find: "kestra-api", replacement: path.resolve(__dirname, "src/generated/kestra-api/")},
            {find: "#imports", replacement: MDC_STUB},
            {find: "#build/mdc-image-component.mjs", replacement: MDC_STUB},
            {find: "#mdc-imports", replacement: MDC_STUB},
            {find: "#mdc-configs", replacement: MDC_STUB},
            {find: "@storybook/addon-actions", replacement: "storybook/actions"},
            // @nuxtjs/mdc is a peer dep of @kestra-io/ui-libs that is not installed here;
            // all its subpaths are stubbed out to prevent Rolldown from erroring on
            // unresolved imports (Vite 8 treats them as errors, not warnings).
            {find: /^@nuxtjs\/mdc(\/.*)?$/, replacement: NUXTJS_MDC_STUB},
        ],
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
                silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"]
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
            "lodash-es",
            "nprogress"
        ],
        exclude: [
            "* > @kestra-io/ui-libs"
        ]
    },
})
