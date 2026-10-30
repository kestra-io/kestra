import {expect, test as base} from "@playwright/test"

import {AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR, STORAGE_STATE} from "../fixtures/auth"
import {KvApi} from "../api/kv.api"
import {FlowsApi} from "../api/flows.api"
import {KvPage} from "../pages/kv.page"

/**
 * Deliberately east of UTC: a DATE serialized through UTC lands on the previous day here,
 * which a suite running in the CI's UTC would never notice.
 */
export const TEST_TIMEZONE = "Europe/Paris"

type KvFixtures = {
    kvApi: KvApi;
    kvPage: KvPage;
}

export const test = base.extend<KvFixtures>({
    // Own context rather than the shared one from fixtures/auth: only this suite needs a pinned timezone.
    context: async ({browser}, use) => {
        const context = await browser.newContext({storageState: STORAGE_STATE, timezoneId: TEST_TIMEZONE})

        await context.addInitScript(([authKey, tourKey, tourState]) => {
            sessionStorage.setItem(authKey, "true")
            localStorage.setItem(tourKey, tourState)
        }, [AUTH_FLAG_KEY, PRODUCT_TOUR_STORAGE_KEY, SKIPPED_PRODUCT_TOUR])

        await use(context)
        await context.close()
    },

    page: async ({context}, use) => {
        const page = await context.newPage()
        await use(page)
        await page.close()
    },

    /*
     * A deliberately cookie-free API context, same as executions.fixture.ts: the shared
     * storageState carries the BASIC_AUTH cookie, and CsrfTokenFilter then rejects every
     * POST/DELETE that has no X-CSRF-TOKEN — while these helpers authenticate with the
     * CSRF-exempt `Authorization: Basic` header.
     */
    request: async ({playwright, baseURL}, use) => {
        const context = await playwright.request.newContext({
            baseURL,
            storageState: {cookies: [], origins: []},
        })
        await use(context)
        await context.dispose()
    },

    kvApi: async ({request, baseURL}, use) => {
        // A namespace only shows up in the drawer's selector once it holds a flow.
        const flowsApi = new FlowsApi(request, baseURL)
        await flowsApi.generateFlowViaApi("hello.yaml", "my-hello-flow-1")

        const kvApi = new KvApi(request, baseURL)
        await use(kvApi)

        await kvApi.removeKvsViaApi()
        await flowsApi.removeFlowsViaApi()
    },

    kvPage: async ({page}, use) => {
        await use(new KvPage(page))
    },
})

export {expect}
