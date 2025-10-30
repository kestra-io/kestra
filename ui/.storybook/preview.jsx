import {setup} from "@storybook/vue3-vite";
import {withThemeByClassName} from "@storybook/addon-themes";
import initApp from "../src/utils/init";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";
import en from "../src/translations/en.json";

window.KESTRA_BASE_PATH = "/ui";
window.KESTRA_UI_PATH = "./";

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
