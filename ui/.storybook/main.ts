import type {StorybookConfig} from "@storybook/vue3-vite";
import path from "path";

const config: StorybookConfig = {
    stories: [
        "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"
    ],
    addons: [
        "@storybook/addon-themes",
        "@storybook/addon-vitest",
        "@storybook/addon-docs"
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
                "override/services/filterLanguagesProvider": path.resolve(__dirname, "../tests/storybook/mocks/services/filterLanguagesProvider.mock.ts"),
                ...config.resolve?.alias
            };
        }

        return config;
    },
};
export default config;
