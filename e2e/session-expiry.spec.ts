import {test, expect} from "@playwright/test"
import {AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR, STORAGE_STATE} from "./fixtures/auth"

const tenant = process.env.E2E_TENANT ?? "main"

// Its own context: dropping the login flag would sign out every other test sharing the worker's.
test("one rejected request asks to sign in again only once", async ({browser}) => {
    const context = await browser.newContext({storageState: STORAGE_STATE, serviceWorkers: "block"})
    await context.addInitScript(([tourKey, tourState]) => {
        localStorage.setItem(tourKey, tourState)
    }, [PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])
    const page = await context.newPage()

    // Without a time range and a user filter in the URL, the list rewrites its own route, and that navigation fails the auth guard once the flag is dropped.
    await page.goto(`/ui/${tenant}/executions?filters[timeRange][EQUALS]=PT24H&filters[flowId][EQUALS]=session-expiry`)
    const refresh = page.getByRole("button", {name: "Refresh data"})
    await expect(refresh).toBeVisible()

    await context.clearCookies({name: AUTH_FLAG_KEY})
    let rejected = false
    await page.route(/\/api\/v1\/.+\/executions\/search/, (route) => {
        if (rejected) return route.continue()
        rejected = true
        return route.fulfill({status: 401, contentType: "application/json", body: "{}"})
    })
    await refresh.click()

    const dialogs = page.locator("[data-test='reauth-dialog']").filter({visible: true})
    await expect(dialogs).toHaveCount(1)
    await dialogs.getByRole("button", {name: "Go to login page"}).click()

    await expect(page).toHaveURL(/\/login/)
    await expect(dialogs).toHaveCount(0)
    await context.close()
})
