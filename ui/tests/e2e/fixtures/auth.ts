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

type SharedPageFixtures = {
    sharedContext: BrowserContext
    sharedPage: Page
}

/**
 * The `test` every spec should import.
 *
 * The authenticated context and page are shared per worker rather than recreated per
 * test: a fresh context means a cold SPA boot (full bundle fetch + parse + the
 * sequential auth/config round-trips in main.ts), which is the dominant per-test cost
 * in CI. Specs keep using the standard `page`/`context` fixtures — they resolve to the
 * worker-shared instances — and reset in-app state in their `beforeEach` instead.
 * Playwright restarts the worker after any test failure, so a broken page never leaks
 * into the following tests.
 */
export const test = base.extend<{forEachTest: void}, SharedPageFixtures>({
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

    sharedPage: [async ({sharedContext}, use) => {
        const page = await sharedContext.newPage()

        // The app arms a native beforeunload confirm while an editor holds unsaved
        // changes (utils/unsavedChange.ts). On a shared page, a dirty editor left by
        // one test would block the next test's hard navigation — accept to proceed.
        // Safe blanket policy: the app uses in-DOM dialogs everywhere else, so no
        // other native dialog can reach this handler.
        page.on("dialog", (dialog) => {
            dialog.accept().catch(() => {})
        })

        await use(page)
        await page.close()
    }, {scope: "worker"}],

    context: async ({sharedContext}, use) => {
        await use(sharedContext)
    },
    page: async ({sharedPage}, use) => {
        await use(sharedPage)
    },

    // Route stubs registered by one test must not leak into the next test on the shared page.
    forEachTest: [async ({sharedPage}, use) => {
        await use()
        await sharedPage.unrouteAll({behavior: "ignoreErrors"})
    }, {auto: true}],
})

export {expect} from "@playwright/test"
