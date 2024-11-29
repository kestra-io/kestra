import type { StorybookConfig } from "@storybook/vue3-vite";
import viteJSXPlugin from "@vitejs/plugin-vue-jsx"

const config: StorybookConfig = {
  stories: ["../src/**/*.mdx", "../src/**/*.stories.@(js|jsx|mjs|ts|tsx)"],
  addons: [
    "@storybook/addon-essentials",
    "@chromatic-com/storybook",
    "@storybook/addon-interactions",
  ],
  framework: {
    name: "@storybook/vue3-vite",
    options: {},
  },
  async viteFinal(config) {
    config.plugins = [
      ...config.plugins ?? [],
      viteJSXPlugin(),
    ];
    return config;
  },
};
export default config;
