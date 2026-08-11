import path from "path"
import {fileURLToPath} from "url"
import {mergeConfig} from "vite"
import type {StorybookConfig} from "@storybook/vue3-vite"

const config: StorybookConfig = {
    stories: [
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)",
    ],
    addons: ["@storybook/addon-themes", "@storybook/addon-vitest"],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(viteConfig) {
        const __dirname = path.dirname(fileURLToPath(import.meta.url))
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")

        viteConfig.plugins = [
            ...(viteConfig.plugins ?? []),
            viteJSXPlugin(),
        ]

        if (viteConfig.resolve) {
            const AliasConfig = [
                ...(viteConfig.resolve.alias as any[]),
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
