import type {StorybookConfig} from "@storybook/vue3-vite";

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
    return config;
  },
};
export default config;
