import {setup} from "@storybook/vue3";
import initApp from "../src/utils/init";
import stores from "../src/stores/store";
import DarkModeSwitch from "../src/components/layout/DarkModeSwitch.vue"

import "../src/styles/vendor.scss";
import "../src/styles/app.scss";

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
    () => ({
        components: {
            DarkModeSwitch,
        },
        template:`<div style="margin: 1rem;">
            <DarkModeSwitch />
            <story/>
        </div>`
    })
  ]
};

setup((app) => {
  initApp(app, [], stores, {en: {}});
});

export default preview;
