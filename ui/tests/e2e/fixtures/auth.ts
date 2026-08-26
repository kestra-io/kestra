import {test as base, type BrowserContext, type Page} from "@playwright/test"
import path from "path"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/** Where the `setup` project parks the authenticated browser state (absolute
 *  so it resolves the same from `ui/` or the repo root). */
export const STORAGE_STATE = path.resolve(__dirname, "../.auth/user.json")

/** Mirrors `AUTH_FLAG_KEY` in `ui/src/utils/basicAuth.ts`. */
export const AUTH_FLAG_KEY = "kestraBasicAuthenticated"

/** Mirrors `STORAGE_KEY` in `ui/src/stores/productTour.ts`. */
export const PRODUCT_TOUR_STORAGE_KEY = "kestra.productTour.state"

/** Seeds the state the tour overlay reads as "already answered", so it never
 *  renders a card over the app under test. */
export const SKIPPED_PRODUCT_TOUR = JSON.stringify({status: "skipped"})

type SharedContextFixtures = {
    sharedContext: BrowserContext
}

/** The `test` every spec should import: login is shared per worker, each
 *  test still gets its own tab (fresh DOM/JS heap, no leaked state). */
export const test = base.extend<{page: Page}, SharedContextFixtures>({
    sharedContext: [async ({browser}, use) => {
        // The production build registers a service worker (workbox `NetworkOnly` for `/api/*`,
        // `clientsClaim: true`). It doesn't control the very first page, but claims every page
        // after that — and once it does, `/api/*` fetches are handled inside the worker's own
        // fetch listener, a layer `page.route()` doesn't see through. Any spec that stubs an API
        // response and then does a second hard navigation would silently stop being stubbed.
        // Blocking service workers for the test context sidesteps this entirely.
        const context = await browser.newContext({storageState: STORAGE_STATE, serviceWorkers: "block"})

        // storageState skips sessionStorage, so the login-flag cookie alone
        // still bounces the SPA to /ui/login — re-seed the flag per document.
        await context.addInitScript(([authKey, tourKey, tourState]) => {
            sessionStorage.setItem(authKey, "true")
            localStorage.setItem(tourKey, tourState)
        }, [AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])

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
