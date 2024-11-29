import {setup, type Preview } from "@storybook/vue3";
import initApp from "../src/utils/init";
import stores from "../src/stores/store";

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";

declare global {
  interface Window {
    KESTRA_BASE_PATH: string;
    KESTRA_UI_PATH: string;
  }
}

window.KESTRA_BASE_PATH = "/ui";
window.KESTRA_UI_PATH = "./";

const preview: Preview = {
  parameters: {
    controls: {
      matchers: {
        color: /(background|color)$/i,
        date: /Date$/i,
      },
    },
  },
};

setup((app) => {
  initApp(app, [], stores, {en: {}});
});

export default preview;
