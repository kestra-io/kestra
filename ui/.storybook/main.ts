// This file has been automatically migrated to valid ESM format by Storybook.
import path, {dirname} from "path"
import {fileURLToPath} from "url"
import {mergeConfig, type Alias} from "vite"
import type {StorybookConfig} from "@storybook/vue3-vite"

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

const config: StorybookConfig = {
    stories: [
        "../src/**/*.stories.@(ts|tsx)",
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)",
    ],
    addons: [getAbsolutePath("@storybook/addon-themes"), getAbsolutePath("@storybook/addon-vitest")],
    framework: {
        name: getAbsolutePath("@storybook/vue3-vite"),
        options: {
            // Storybook resolves this tsconfig from the git root, not from this file
            docgen: {
                plugin: "vue-component-meta",
                tsconfig: "ui/.storybook/tsconfig.docgen.json",
            },
        },
    },
    async viteFinal(viteConfig) {
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")

        viteConfig.plugins = [
            ...(viteConfig.plugins ?? []),
            viteJSXPlugin(),
        ]

        if (viteConfig.resolve) {
            const AliasConfig = [
                ...(viteConfig.resolve.alias as Alias[] ?? []),
                {find: "override", replacement: path.resolve(__dirname, "../src/override/")},
            ]
            viteConfig.resolve.alias = AliasConfig
        }

        // Silence "Sourcemap for X points to a source file outside its package"
        // warnings from node_modules — cross-package scss sourcemap references.
        if (viteConfig.customLogger) {
            const isElementPlusSourcemapWarning = (msg: string) =>
                /sourcemap/i.test(msg) && msg.includes("points to a source file outside its package") && msg.includes("node_modules")
            const origWarn = viteConfig.customLogger.warn.bind(viteConfig.customLogger)
            const origWarnOnce = viteConfig.customLogger.warnOnce.bind(viteConfig.customLogger)
            viteConfig.customLogger.warn = (msg, opts) => { if (!isElementPlusSourcemapWarning(msg)) origWarn(msg, opts) }
            viteConfig.customLogger.warnOnce = (msg, opts) => { if (!isElementPlusSourcemapWarning(msg)) origWarnOnce(msg, opts) }
        }

        return mergeConfig(viteConfig, {
            define: {"process.env": {}},
        })
    },
}
export default config

function getAbsolutePath(value: string): string {
    return dirname(fileURLToPath(import.meta.resolve(`${value}/package.json`)))
}
