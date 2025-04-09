import path from "node:path";

import {defineWorkspace} from "vitest/config";

import {storybookTest} from "@storybook/experimental-addon-test/vitest-plugin";

// More info at: https://storybook.js.org/docs/writing-tests/test-addon
export default defineWorkspace([
    "vitest.config.js",
    {
        extends: "vite.config.js",
        plugins: [
            // The plugin will run tests for the stories defined in your Storybook config
            // See options at: https://storybook.js.org/docs/writing-tests/test-addon#storybooktest
            storybookTest({configDir: path.join(__dirname, ".storybook")}),
        ],
        test: {
            name: "storybook",
            browser: {
                enabled: true,
                headless: true,
                provider: "playwright",
                instances: [{browser: "chromium"}],
            },
            setupFiles: [".storybook/vitest.setup.ts"],
        },
    },
]);
