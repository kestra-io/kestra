import {mergeConfig} from "vite"
import {resolve} from "path"
import type {StorybookConfig} from "@storybook/vue3-vite"

const srcDir = resolve(import.meta.dirname, "../src")

const config: StorybookConfig = {
    stories: ["../tests/storybook/**/*.stories.@(ts|tsx)"],
    addons: [
        "@storybook/addon-themes",
        "@storybook/addon-vitest",
        "@storybook/addon-a11y",
        "@storybook/addon-docs",
    ],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(viteConfig) {
        return mergeConfig(viteConfig, {
            define: {"process.env": {}},
            css: {
                devSourcemap: true,
                preprocessorOptions: {
                    scss: {
                        loadPaths: [srcDir],
                        silenceDeprecations: ["color-functions", "global-builtin", "if-function", "import"],
                    },
                },
            },
        })
    },
}

export default config
