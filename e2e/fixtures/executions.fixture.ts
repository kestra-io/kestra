import {test as base} from "./auth"
import {ExecutionsPage} from "../pages/executions.page"
import {ExecutionsApi} from "../api/executions.api"
import {FlowsApi} from "../api/flows.api"

type ExecutionsFixtures = {
  executionsApi: ExecutionsApi,
  executionsPage: ExecutionsPage,
  flow: {fileName: string, flowId: string}
};

export const test = base.extend<ExecutionsFixtures>({
    // define the default `flow` option
    flow: [{fileName: "hello.yaml", flowId: "my-hello-flow-1"}, {option: true}],
    /*
     * A deliberately cookie-free API context.
     *
     * The built-in `request` fixture inherits `use.storageState`, which now carries the
     * BASIC_AUTH cookie. CsrfTokenFilter#hasCookieAuth then treats every POST/DELETE as
     * cookie-authenticated and rejects it with a 403 unless it also carries an
     * X-CSRF-TOKEN — but these helpers authenticate with the CSRF-exempt
     * `Authorization: Basic` header instead. A fresh context keeps that path open.
     */
    request: async ({playwright, baseURL}, use) => {
        const context = await playwright.request.newContext({
            baseURL,
            // Explicitly empty: `newContext` otherwise picks up the config's `use.storageState`.
            storageState: {cookies: [], origins: []},
        })
        await use(context)
        await context.dispose()
    },
    executionsApi: async ({page, request,  baseURL, flow}, use) => {
        // Prepare data
        const flowsApi = new FlowsApi(request, baseURL)
        const executionsPage = new ExecutionsPage(page)
        const executionsApi = new ExecutionsApi(request, await flowsApi.generateFlowViaApi(flow.fileName, flow.flowId), baseURL)
        await executionsApi.generateExecutionViaApi()

        // Navigate
        await executionsPage.goto()

        // Do the work
        await use(executionsApi)

        // Clean up
        await executionsApi.removeExecutionsViaApi()
        await flowsApi.removeFlowsViaApi()
    },
    executionsPage: async ({page}, use) => {
        const executionsPage = new ExecutionsPage(page)

        await use(executionsPage)
    },
})

export {expect} from "@playwright/test"