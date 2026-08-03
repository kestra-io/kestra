import {test as base, type BrowserContext, type Page} from "@playwright/test"
import path from "path"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** Where the `setup` project parks the authenticated browser state (absolute
 *  so it resolves the same from `ui/` or the repo root). */
export const STORAGE_STATE = path.resolve(__dirname, "../.auth/user.json")

/** Mirrors `AUTH_FLAG_KEY` in `ui/src/utils/basicAuth.ts`. */
const AUTH_FLAG_KEY = "kestraBasicAuthenticated"

const PRODUCT_TOUR_STORAGE_KEY = "kestra.productTour.state"

type SharedContextFixtures = {
    sharedContext: BrowserContext
}

/** The `test` every spec should import: login is shared per worker, each
 *  test still gets its own tab (fresh DOM/JS heap, no leaked state). */
export const test = base.extend<{page: Page}, SharedContextFixtures>({
    sharedContext: [async ({browser}, use) => {
        const context = await browser.newContext({storageState: STORAGE_STATE})

        // storageState skips sessionStorage, so the login-flag cookie alone
        // still bounces the SPA to /ui/login — re-seed the flag per document.
        await context.addInitScript(([authKey, tourKey]) => {
            sessionStorage.setItem(authKey, "true")
            localStorage.setItem(tourKey, JSON.stringify({status: "skipped"}))
        }, [AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY])

        await use(context)
        await context.close()
    }, {scope: "worker"}],

    context: async ({sharedContext}, use) => {
        await use(sharedContext)
    },

    page: async ({sharedContext}, use) => {
        const page = await sharedContext.newPage()

        // Auto-accept the native beforeunload confirm an unsaved editor arms,
        // so leaving a dirty editor never hangs a test.
        page.on("dialog", (dialog) => {
            dialog.accept().catch(() => {})
        })

        await use(page)
        await page.close()
    },
})

export {expect} from "@playwright/test"
