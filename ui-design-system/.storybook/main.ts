import {mergeConfig} from "vite"
import type {StorybookConfig} from "@storybook/vue3-vite"

const config: StorybookConfig = {
    stories: ["../tests/storybook/**/*.stories.@(ts|tsx)"],
    addons: [
        "@storybook/addon-themes",
        "@storybook/addon-vitest",
        "@chromatic-com/storybook",
        "@storybook/addon-a11y",
        "@storybook/addon-docs"
    ],
    framework: {
        name: "@storybook/vue3-vite",
        options: {},
    },
    async viteFinal(config) {
        return mergeConfig(config, {
            define: {"process.env": {}},
        })
    },
}

export default config
