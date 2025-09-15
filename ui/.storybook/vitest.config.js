import path from "node:path";

import {defineProject, mergeConfig} from "vitest/config";

import {storybookTest} from "@storybook/addon-vitest/vitest-plugin";
import initialConfig from "../vite.config.js"


// More info at: https://storybook.js.org/docs/writing-tests/test-addon
export default mergeConfig(
    // We need to define a side first project to set up the alias for the filterLanguagesProvider mock because otherwise the `override` alias will take precedence over this one (first match rule)
    defineProject({
        resolve: {
            alias: {
                "override/services/filterLanguagesProvider": path.resolve(__dirname, "../tests/storybook/mocks/services/filterLanguagesProvider.mock.ts")
            }
        }
    }),
    mergeConfig(
        initialConfig,
        defineProject({
            plugins: [
                // The plugin will run tests for the stories defined in your Storybook config
                // See options at: https://storybook.js.org/docs/writing-tests/test-addon#storybooktest
                storybookTest({configDir: path.join(__dirname)}),
            ],
            test: {
                name: "storybook",
                browser: {
                    enabled: true,
                    headless: true,
                    provider: "playwright",
                    instances: [{browser: "chromium"}],
                },
                setupFiles: ["vitest.setup.ts"],
            },
            define: {
                "process.env.RUN_TEST_WITH_PERSISTENT": JSON.stringify("false"), // Disable persistent mode for tests
            }
        }),
    ),
);
