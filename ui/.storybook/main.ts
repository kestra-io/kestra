import {mergeConfig} from "vite";
import type {StorybookConfig} from "@storybook/vue3-vite";

const config: StorybookConfig = {
    stories: [
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"
    ],
    addons: [
        "@storybook/addon-themes",
        "@storybook/addon-vitest",
    ],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(config) {
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")
        config.plugins = [
            ...(config.plugins ?? []),
            viteJSXPlugin(),
        ];

        if (config.resolve) {
            config.resolve.alias = {
                ...config.resolve?.alias
            };
        }

        return mergeConfig(config, {
            define: {"process.env": {}},
        });
    },
};
export default config;
