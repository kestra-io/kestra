import {test as base} from "@playwright/test"
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

/**
 * The `test` every spec should import.
 *
 * Signing in leaves two traces: the HttpOnly `BASIC_AUTH` cookie issued by the
 * server, and a `sessionStorage` flag the router guard reads to decide whether a
 * login round-trip ever happened. Playwright's `storageState` persists cookies and
 * localStorage but *not* sessionStorage, so restoring it alone would hand the
 * browser a valid session that the SPA still bounces to `/ui/login`. Re-seed the
 * flag on every document instead.
 */
export const test = base.extend({
    context: async ({context}, use) => {
        await context.addInitScript(([authKey, tourKey]) => {
            sessionStorage.setItem(authKey, "true")
            localStorage.setItem(tourKey, JSON.stringify({status: "skipped"}))
        }, [AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY])

        await use(context)
    },
})

export {expect} from "@playwright/test"
