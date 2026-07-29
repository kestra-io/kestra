import dotenv from "dotenv"
const __dirname = new URL(".", import.meta.url).pathname
dotenv.config({path: __dirname + "/.env"})

import type {PlaywrightTestConfig} from "@playwright/test"
import {devices} from "@playwright/test"
import {STORAGE_STATE} from "./fixtures/auth"


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
    timeout: 60 * 1000,
    expect: {
        /**
         * Maximum time expect() should wait for the condition to be met.
         * For example in `await expect(locator).toHaveText();`
         */
        timeout: 15000,
        toHaveScreenshot: {
            maxDiffPixelRatio: 0.02,
        },
    },
    /* Run tests in files in parallel */
    fullyParallel: true,
    /* Fail the build on CI if you accidentally left test.only in the source code. */
    forbidOnly: !!process.env.CI,
    /* Retry on CI only. Kept low: a genuinely broken test costs a full run per retry. */
    retries: process.env.CI ? 2 : 0,
    /*
     * The CI runner has 4 vCPUs and also hosts the Kestra JVM, Postgres and dind, so the
     * browsers compete with the backend under test. Two is the sweet spot; more makes the
     * execution-heavy specs slower and flakier rather than faster.
     */
    workers: process.env.CI ? 2 : "50%",
    /*
     * Fail with an HTML report rather than being killed by the job's own 30-minute ceiling
     * (set in kestra-io/actions), which leaves no artefacts at all.
     */
    globalTimeout: process.env.CI ? 20 * 60 * 1000 : undefined,
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

        /*
         * `on-first-retry` rather than `retain-on-failure`: the latter records a
         * screencast and trace snapshots for *every* test and throws them away on
         * pass, which every green test pays for.
         */
        trace: "on-first-retry",
        /* Capture screenshot after each test failure */
        screenshot: "only-on-failure",
        video: "on-first-retry",
    },

    /* Configure projects for major browsers */
    projects: [
        /* Signs in once and parks the cookie jar the other projects reuse. */
        {
            name: "setup",
            testMatch: /auth\.setup\.ts/,
        },
        {
            name: "chromium",
            use: {...devices["Desktop Chrome"], storageState: STORAGE_STATE},
            dependencies: ["setup"],
        },
    ],

    /* Run your local dev server before starting the tests */
    // webServer: {
    //   command: "npm run dev",
    //   port: 8080,
    //   reuseExistingServer: !process.env.CI,
    // },
}

export default config
