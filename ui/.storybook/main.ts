import {mergeConfig} from "vite";
import type {StorybookConfig} from "@storybook/vue3-vite";
import path from "path";

const config: StorybookConfig = {
    stories: [
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"
    ],
    addons: ["@storybook/addon-themes", "@storybook/addon-vitest"],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(config) {
        const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx");
        config.plugins = [
            ...(config.plugins ?? []),
            viteJSXPlugin(),
        ];

        config.resolve = config.resolve || {};
        config.resolve.alias = {
            ...(config.resolve.alias || {}),
            "@": path.resolve(__dirname, "../src"),
        };

        return mergeConfig(config, {
            define: {"process.env": {}},
        });
    },
};
export default config;
