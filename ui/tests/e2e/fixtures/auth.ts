import {test as base, type BrowserContext, type Page} from "@playwright/test"
import path from "path"
import {fileURLToPath} from "url"

const __dirname = path.dirname(fileURLToPath(import.meta.url))

/**
 * Where the `setup` project parks the authenticated browser state.
 *
 * Absolute so it resolves the same whether Playwright is invoked from `ui/` or
 * from the repository root.
 */
export const STORAGE_STATE = path.resolve(__dirname, "../.auth/user.json")

/**
 * Mirrors `AUTH_FLAG_KEY` in `ui/src/utils/basicAuth.ts`.
 */
const AUTH_FLAG_KEY = "kestraBasicAuthenticated"

const PRODUCT_TOUR_STORAGE_KEY = "kestra.productTour.state"

type SharedContextFixtures = {
    sharedContext: BrowserContext
}

/**
 * The `test` every spec should import.
 *
 * The authenticated context is shared per worker: the login and its warm browser
 * caches (HTTP + compiled code) are paid once, so a fresh tab boots the SPA in a
 * couple of seconds instead of the ~9s a cold context pays. Each test still gets its
 * OWN page (tab): fresh DOM, JS heap, stores and sessionStorage — no in-app state can
 * leak between tests. Only cookies and localStorage are shared, which is the point
 * (the login). Playwright restarts the worker after any test failure, so even the
 * shared context never survives a failing test.
 */
export const test = base.extend<{page: Page}, SharedContextFixtures>({
    sharedContext: [async ({browser}, use) => {
        const context = await browser.newContext({storageState: STORAGE_STATE})

        // Signing in leaves two traces: the HttpOnly `BASIC_AUTH` cookie issued by the
        // server, and a `sessionStorage` flag the router guard reads to decide whether a
        // login round-trip ever happened. `storageState` restores cookies and
        // localStorage but *not* sessionStorage, so restoring it alone would hand the
        // browser a valid session that the SPA still bounces to `/ui/login`. Re-seed the
        // flag on every document instead.
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

        // The app arms a native beforeunload confirm while an editor holds unsaved
        // changes (utils/unsavedChange.ts); accept it so a test navigating away from
        // its own dirty editor never hangs. Safe blanket policy: the app uses in-DOM
        // dialogs everywhere else, so no other native dialog can reach this handler.
        page.on("dialog", (dialog) => {
            dialog.accept().catch(() => {})
        })

        await use(page)
        await page.close()
    },
})

export {expect} from "@playwright/test"
