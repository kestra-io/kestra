import dotenv from "dotenv"
const __dirname = new URL(".", import.meta.url).pathname;
dotenv.config({path: __dirname + "/.env"});

import type {PlaywrightTestConfig} from "@playwright/test";
import {devices} from "@playwright/test";


/**
 * Read environment variables from file.
 * https://github.com/motdotla/dotenv
 */
// require('dotenv').config();

/**
 * @see https://playwright.dev/docs/test-configuration
 */
const config: PlaywrightTestConfig = {
    testDir: "./",
    /* Maximum time one test can run for. */
    timeout: 30 * 1000,
    expect: {
        /**
         * Maximum time expect() should wait for the condition to be met.
         * For example in `await expect(locator).toHaveText();`
         */
        timeout: 5000,
        toHaveScreenshot: {
            maxDiffPixelRatio: 0.02,
        },
    },
    /* Run tests in files in parallel */
    fullyParallel: true,
    /* Fail the build on CI if you accidentally left test.only in the source code. */
    forbidOnly: !!process.env.CI,
    /* Retry on CI only */
    retries: process.env.CI ? 5 : 0,
    /* Opt out of parallel tests on CI. */
    workers: process.env.CI ? 1 : "50%",
    /* Reporter to use. See https://playwright.dev/docs/test-reporters */
    reporter: [
        ["html", {open: "never"}],
        ["list"],
        (process.env.CI ? ["github"] : ["null"]),
    ],
    /* Shared settings for all the projects below. */
    use: {
        /* Base URL to use in actions like `await page.goto("/")`. */
        baseURL: process.env.E2E_BASE_URL ?? "http://localhost:9011",

        /* Collect trace when retrying the failed test. See https://playwright.dev/docs/trace-viewer */
        trace: "retain-on-failure",
        /* Capture screenshot after each test failure */
        screenshot: "only-on-failure",
        /* Collect video when retrying the failed test */
        video: "retain-on-failure",
        launchOptions: {
            slowMo: 100,
        },
    },

    /* Configure projects for major browsers */
    projects: [
        {
            name: "chromium",
            use: {...devices["Desktop Chrome"]},
        },
    ],

    /* Run your local dev server before starting the tests */
    // webServer: {
    //   command: "npm run dev",
    //   port: 8080,
    //   reuseExistingServer: !process.env.CI,
    // },
};

export default config;
