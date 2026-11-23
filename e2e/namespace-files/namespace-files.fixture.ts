import {v4 as uuid} from "uuid"
import {expect, test as base} from "../fixtures/auth"
import {FlowsApi} from "../api/flows.api"
import {NamespaceFilesApi} from "../api/namespace-files.api"

type NamespaceFilesFixtures = {
    /** A namespace of its own per test, so the file tree and the stored panel layout start empty. */
    namespace: string;
    filesApi: NamespaceFilesApi;
    flowsApi: FlowsApi;
}

export const test = base.extend<NamespaceFilesFixtures>({
    /*
     * The file endpoint revalidates the browser cache with a one-second Last-Modified, so a file
     * saved in the same second as its cached copy reads back stale after a reload; the cache is
     * off so that a save can be read back right away.
     */
    page: async ({page}, use) => {
        const cdp = await page.context().newCDPSession(page)
        await cdp.send("Network.enable")
        await cdp.send("Network.setCacheDisabled", {cacheDisabled: true})
        await use(page)
        await cdp.detach()
    },

    /*
     * Cookie-free, same as kv.fixture.ts: the shared storageState carries the BASIC_AUTH cookie,
     * and CsrfTokenFilter rejects any POST/DELETE that has it without an X-CSRF-TOKEN.
     */
    request: async ({playwright, baseURL}, use) => {
        const context = await playwright.request.newContext({
            baseURL,
            storageState: {cookies: [], origins: []},
        })
        await use(context)
        await context.dispose()
    },

    filesApi: async ({request, baseURL}, use) => {
        const filesApi = new NamespaceFilesApi(request, baseURL, `e2e.files.${uuid().slice(0, 8)}`)
        await use(filesApi)
        await filesApi.removeAllViaApi()
    },

    namespace: async ({filesApi}, use) => {
        await use(filesApi.namespace)
    },

    flowsApi: async ({request, baseURL}, use) => {
        const flowsApi = new FlowsApi(request, baseURL)
        await use(flowsApi)
        await flowsApi.removeFlowsViaApi()
    },
})

export {expect}
