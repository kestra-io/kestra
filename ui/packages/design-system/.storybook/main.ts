import {fileURLToPath} from "node:url"
import {mergeConfig} from "vite"
import {resolve, dirname} from "path"
import type {StorybookConfig} from "@storybook/vue3-vite"

const srcDir = resolve(import.meta.dirname, "../src")

const config: StorybookConfig = {
    stories: ["../src/**/*.stories.@(ts|tsx)", "../tests/storybook/**/*.stories.@(ts|tsx)"],
    addons: [
        getAbsolutePath("@storybook/addon-themes"),
        getAbsolutePath("@storybook/addon-vitest"),
        getAbsolutePath("@storybook/addon-a11y"),
        getAbsolutePath("@storybook/addon-docs"),
    ],
    framework: {
        name: getAbsolutePath("@storybook/vue3-vite"),
        options: {
            // Storybook resolves this tsconfig from the git root, not from this file
            docgen: {
                plugin: "vue-component-meta",
                tsconfig: "ui/packages/design-system/tsconfig.app.json",
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

function getAbsolutePath(value: string): string {
    return dirname(fileURLToPath(import.meta.resolve(`${value}/package.json`)))
}
