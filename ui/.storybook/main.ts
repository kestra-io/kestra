import type {StorybookConfig} from "@storybook/vue3-vite";

const config: StorybookConfig = {
  stories: [
    "../tests/**/*.stories.@(js|jsx|mjs|ts|tsx)"
],
  addons: [
    "@storybook/addon-essentials",
    "@storybook/addon-themes",
    "@chromatic-com/storybook",
    "@storybook/addon-interactions",
  ],
  framework: {
    name: "@storybook/vue3-vite",
    options: {},
  },
  async viteFinal(config) {
    const {default: viteJSXPlugin} = await import("@vitejs/plugin-vue-jsx")
    config.plugins = [
      ...config.plugins ?? [],
      viteJSXPlugin(),
    ];
    return config;
  },
};
export default config;
