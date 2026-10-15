import {mergeConfig} from "vite"
import {relative, resolve} from "path"
import type {StorybookConfig} from "@storybook/vue3-vite"
import {getProjectRoot} from "storybook/internal/common"

const srcDir = resolve(import.meta.dirname, "../src")

const config: StorybookConfig = {
    stories: ["../src/**/*.stories.@(ts|tsx)", "../tests/storybook/**/*.stories.@(ts|tsx)"],
    addons: [
        "@storybook/addon-themes",
        "@storybook/addon-vitest",
        "@storybook/addon-a11y",
        "@storybook/addon-docs",
    ],
    framework: {
        name: "@storybook/vue3-vite",
        options: {
            // Storybook resolves this tsconfig from the git root, not from this file
            docgen: {
                plugin: "vue-component-meta",
                tsconfig: `${relative(getProjectRoot(), resolve(import.meta.dirname))}/tsconfig.app.json`,
            },
        },
    },
    async viteFinal(viteConfig) {
        viteConfig.plugins = [
            ...(viteConfig.plugins ?? []),
            (await import("@vitejs/plugin-vue-jsx")).default(),
        ]
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
