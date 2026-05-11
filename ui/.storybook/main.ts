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
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")
        viteConfig.plugins = [
            ...(viteConfig.plugins ?? []),
            viteJSXPlugin(),
        ]

        if (viteConfig.resolve) {
            viteConfig.resolve.alias = {
                ...viteConfig.resolve?.alias,
            }
        }

        return mergeConfig(viteConfig, {
            define: {"process.env": {}},
        })
    },
}
export default config
