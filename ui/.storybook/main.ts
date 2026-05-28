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

        return mergeConfig(viteConfig, {
            define: {"process.env": {}},
        })
    },
}
export default config
