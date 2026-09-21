import {expect, test as base} from "@playwright/test"
import {PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR} from "../fixtures/auth"

/*
 * A deliberately cookie-free API context, same as executions.fixture.ts.
 *
 * The built-in `request` fixture inherits `use.storageState`, which carries the
 * BASIC_AUTH cookie. CsrfTokenFilter#hasCookieAuth then treats every POST/DELETE as
 * cookie-authenticated and rejects it with a 403 unless it also carries an
 * X-CSRF-TOKEN — but FlowsApi authenticates with the CSRF-exempt
 * `Authorization: Basic` header instead. A fresh context keeps that path open.
 *
 * `page` keeps the shared storageState, so login() and the UI are unaffected.
 *
 * This suite builds its own context rather than reusing fixtures/auth (its specs sign in
 * through the form), so it also has to neutralise the product tour itself: left running,
 * its card covers the canvas and swallows every click.
 */
export const test = base.extend({
    context: async ({context}, use) => {
        await context.addInitScript(([tourKey, tourState]) => {
            localStorage.setItem(tourKey, tourState)
        }, [PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])
        await use(context)
    },

    request: async ({playwright, baseURL}, use) => {
        const context = await playwright.request.newContext({
            baseURL,
            // Explicitly empty: `newContext` otherwise picks up the config's `use.storageState`.
            storageState: {cookies: [], origins: []},
        })
        await use(context)
        await context.dispose()
    },
})

export {expect}
