import {expect, test as setup} from "@playwright/test"
import {shared} from "./fixtures/shared"
import {PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR, STORAGE_STATE} from "./fixtures/auth"

/**
 * Signs in once per run and parks the resulting cookie jar for every other project
 * to reuse, so no spec pays for the login form.
 */
setup("authenticate", async ({page, context}) => {
    // On an empty instance the post-login redirect lands on the AI Copilot page, where the
    // product tour auto-starts — and the guided card it opens would be captured below and
    // inherited by every project, covering the app under test.
    await context.addInitScript(([tourKey, tourState]) => {
        localStorage.setItem(tourKey, tourState)
    }, [PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])

    await page.goto("/ui")

    await page.getByRole("textbox", {name: "Email"}).fill(shared.username)
    await page.getByRole("textbox", {name: "Password"}).fill(shared.password)
    await page.getByRole("button", {name: "Login"}).click()

    // Anchor on the app shell, not the dashboard: on an instance with no flows yet the
    // post-login redirect lands on the AI Copilot welcome page instead of the dashboard.
    await expect(page.getByRole("button", {name: "Toggle panel"})).toBeVisible({timeout: 30000})

    await context.storageState({path: STORAGE_STATE})
})
