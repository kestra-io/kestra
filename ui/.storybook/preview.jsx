import {setup} from "@storybook/vue3-vite";
import {withThemeByClassName} from "@storybook/addon-themes";
import initApp from "../src/utils/init";
import {configureAxios} from "@kestra-io/kestra-sdk";
import axios from "axios";
import {vueRouter} from "storybook-vue3-router";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";
import en from "../src/translations/en.json";

window.KESTRA_BASE_PATH = "/ui";
window.KESTRA_UI_PATH = "./";

// Intercept all /api requests and return empty successful responses.
// No backend is running during storybook tests, so this prevents network
// errors, proxy failures, and the Vue/axios error cascade that follows.
const originalAdapter = axios.defaults.adapter;
axios.defaults.adapter = async (config) => {
    if (typeof config.url === "string" && config.url.includes("/api/")) {
        return {data: [], status: 200, statusText: "OK", headers: {}, config, request: {}};
    }
    return originalAdapter(config);
};

/**
 * @type {import('@storybook/vue3-vite').Preview}
 */
const preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
  decorators: [
    vueRouter(),
    withThemeByClassName({
        themes: {
          light: "light",
          dark: "dark",
        },
        defaultTheme: "light",
      })
  ]
};

setup(async (app) => {
  const {piniaStore} = await initApp(app, [], {}, en);
  configureAxios({},  {oss:true})
  piniaStore.use(({store}) => {
    store.$http = {
        get: () => Promise.resolve({data: []}),
    }
  });
})

window.addEventListener("unhandledrejection", (evt) => {
    if (evt?.reason?.stack?.includes?.("/monaco/esm/vs") || evt?.reason?.stack?.includes?.("/monaco/min/vs")) {
        evt.stopImmediatePropagation()
    }
})

export default preview;
