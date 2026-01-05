import {test as base} from "@playwright/test";
import {ExecutionsPage} from "../pages/executions.page";
import {ExecutionsApi} from "../api/executions.api";
import {FlowsApi} from "../api/flows.api";

type ExecutionsFixtures = {
  executionsApi: ExecutionsApi,
  executionsPage: ExecutionsPage,
  flow: {fileName: string, flowId: string}
};

export const test = base.extend<ExecutionsFixtures>({
    // define the default `flow` option
    flow: [{fileName: "hello.yaml", flowId: "my-hello-flow-1"}, {option: true}],
    executionsApi: async ({page, request,  baseURL, flow}, use) => {
        // Prepare data
        const flowsApi = new FlowsApi(request, baseURL);
        const executionsPage = new ExecutionsPage(page);
        const executionsApi = new ExecutionsApi(request, await flowsApi.generateFlowViaApi(flow.fileName, flow.flowId), baseURL);
        await executionsApi.generateExecutionViaApi();

        // Navigate
        await executionsPage.goto();

        // Do the work
        await use(executionsApi);

        // Clean up
        await executionsApi.removeExecutionsViaApi();
        await flowsApi.removeFlowsViaApi();
    },
    executionsPage: async ({page}, use) => {
        const executionsPage = new ExecutionsPage(page);

        await use(executionsPage);
    }
});

export {expect} from "@playwright/test";