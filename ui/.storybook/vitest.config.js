import path from "node:path";

import {defineConfig} from "vite";
import {defineProject, mergeConfig} from "vitest/config";

import {storybookTest} from "@storybook/addon-vitest/vitest-plugin";
import initialConfig from "../vite.config.js"


// More info at: https://storybook.js.org/docs/writing-tests/test-addon
export default mergeConfig(
    initialConfig,
    defineConfig({
        test: {
            name: "storybook",
            browser: {
                enabled: true,
                headless: true,
                name: "chromium",
                provider: "playwright",
            },
            include: ["../**/*.stories.?(m)[jt]s?(x)"],
            setupFiles: ["./.storybook/vitest.setup.ts"],
            testTimeout: 30000,
            hookTimeout: 30000,
        },
    }),
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
                testTimeout: 30000,
                hookTimeout: 30000,
            },
            define: {
                "process.env.RUN_TEST_WITH_PERSISTENT": JSON.stringify("false"), // Disable persistent mode for tests
            }
        })
);
