// @ts-check

import path from "path"
import {loadEnv} from "vite"
import vue from "@vitejs/plugin-vue"

import {loaderFragment} from "./plugins/loaderFragment.js"
import {devBootStatus} from "./plugins/devBootStatus.js"

/** The Vite config ui/ and ui-ee/ both extend, so a singleton deduped here is deduped in both editions.
 * @param {{root: string, mode: string, apiProxyPath: string}} options `root` is the extending project, `apiProxyPath` the proxy key for API calls */
export function baseConfig({root, mode, apiProxyPath}) {
    process.env = {...process.env, ...loadEnv(mode, process.cwd())}
    const target = process.env.VITE_PROXY_URL || "http://localhost:8080"

    return {
        server: {
            proxy: {
                [apiProxyPath]: {
                    target,
                    ws: true,
                    changeOrigin: true,
                },
                /** Lets the SDK's dev-only staleness check fetch the backend's OpenAPI spec to compare its hash.
                 * The check is tree-shaken from production builds. */
                "^/swagger": {
                    target,
                    changeOrigin: true,
                },
            },
        },
        resolve: {
            dedupe: ["echarts", "vue-echarts", "dayjs", "vue", "vue-router", "vue-i18n", "@vueuse/core", "pinia", "@vue-flow/core", "@vue-flow/background", "@vue-flow/controls"],
            alias: [
                {find: "override", replacement: path.resolve(root, "src/override/")},
            ],
        },
        plugins: [
            loaderFragment(),
            devBootStatus(target),
            vue({
                template: {
                    compilerOptions: {
                        isCustomElement: (tag) => {
                            return tag === "rapi-doc"
                        },
                    },
                },
            }),
        ],
        css: {
            devSourcemap: true,
            preprocessorOptions: {
                scss: {
                    silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"],
                    loadPaths: [path.resolve(root, "node_modules")],
                },
            },
        },
        optimizeDeps: {
            entries: [
                "src/**/*.stories.{ts,tsx}",
                "tests/storybook/**/*.stories.{ts,tsx}",
                "node_modules/@kestra-io/design-system/src/**/*.{ts,vue}",
                "!node_modules/@kestra-io/design-system/src/**/*.test.ts",
            ],
            include: [
                "debug",
                "@braintree/sanitize-url",
                /** Still pulled in by element-plus and mermaid, not by our own code. */
                "lodash-es",
                /** CJS-only, imported as ESM defaults by unified and fault: pre-bundled as ESM so Chromium (storybook) can load them. */
                "extend",
                "format",
                "humanize-duration",
                "dagre",
                "@vue-flow/background",
                "@vue-flow/controls",
                "html-to-image",
                "js-yaml",
            ],
            exclude: [
                "@kestra-io/design-system",
                "@kestra-io/topology",
                "monaco-editor",
                "monaco-worker-manager",
                "monaco-marker-data-provider",
            ],
        },
    }
}
