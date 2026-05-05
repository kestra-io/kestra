import {defineConfig} from "vite";
import vue from "@vitejs/plugin-vue";

import {mergeConfig} from "vitest/config";
import viteConfig from "./vite.config.js";
import path from "node:path";
import {fileURLToPath} from "node:url";
import {storybookTest} from "@storybook/addon-vitest/vitest-plugin";
import {playwright} from "@vitest/browser-playwright";

const dirname =
    typeof __dirname !== "undefined"
        ? __dirname
        : path.dirname(fileURLToPath(import.meta.url));

const resolvedViteConfig = typeof viteConfig === "function" ? viteConfig({mode: "test"}) : viteConfig;

// More info at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon
export default defineConfig({
    plugins: [vue()],
    resolve: resolvedViteConfig.resolve,
    coverage: {
        exclude: ["**/*.json"],
    },
    test: {
        projects: [
            "./vitest.config.unit.js",
            mergeConfig(resolvedViteConfig, {
                plugins: [
                    // The plugin will run tests for the stories defined in your Storybook config
                    // See options at: https://storybook.js.org/docs/next/writing-tests/integrations/vitest-addon#storybooktest
                    storybookTest({
                        configDir: path.join(dirname, ".storybook"),
                    }),
                ],
                test: {
                    name: "storybook",
                    browser: {
                        enabled: true,
                        headless: true,
                        provider: playwright(),
                        instances: [
                            {
                                browser: "chromium",
                            },
                        ],
                    },
                },
            }),
        ],
    },
    define: {
        "window.KESTRA_BASE_PATH": "/ui/",
    },
});
