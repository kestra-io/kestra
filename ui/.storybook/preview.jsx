import {setup} from "@storybook/vue3";
import {withThemeByClassName} from "@storybook/addon-themes";
import initApp from "../src/utils/init";
import stores from "../src/stores/store";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";
import en from "../src/translations/en.json";

window.KESTRA_BASE_PATH = "/ui";
window.KESTRA_UI_PATH = "./";

/**
 * @type {import('@storybook/vue3').Preview}
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

setup((app) => {
  initApp(app, [], stores, en);
});


window.addEventListener("unhandledrejection", (evt) => {
    if (evt?.reason?.stack?.includes?.("/monaco/esm/vs") || evt?.reason?.stack?.includes?.("/monaco/min/vs")) {
        evt.stopImmediatePropagation()
    }
})

export default preview;
