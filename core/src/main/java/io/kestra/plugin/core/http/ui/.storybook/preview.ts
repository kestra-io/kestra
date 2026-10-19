import type { Preview } from "@storybook/vue3";
import { setup } from "@storybook/vue3";
import { initApp } from "@kestra-io/artifact-sdk";

setup((app) => {
    initApp(app);
});

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

export default preview;