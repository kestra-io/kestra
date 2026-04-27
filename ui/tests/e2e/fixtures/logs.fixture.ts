import {test as base} from "@playwright/test";
import {LogsPage} from "../pages/logs.page";
import {LogsApi} from "../api/logs.api";
import {ExecutionsApi} from "../api/executions.api";
import {FlowsApi} from "../api/flows.api";
import {shared} from "./shared";

type LogsFixtures = {
  flowsApi: FlowsApi,
  executionsApi: ExecutionsApi,
  logsApi: LogsApi,
  logsPage: LogsPage,
  flow: {fileName: string, flowId: string},
  executionCount: number
};

export const test = base.extend<LogsFixtures>({
    flow: [{fileName: "hello.yaml", flowId: "my-hello-flow-1"}, {option: true}],
    executionCount: [3, {option: true}],
    flowsApi: async ({request, baseURL}, use) => {
        const flowsApi = new FlowsApi(request, baseURL);
        await use(flowsApi);
        await flowsApi.removeFlowsViaApi();
    },
    executionsApi: async ({request, baseURL, flowsApi, flow, executionCount, logsPage}, use) => {
        const executionsApi = new ExecutionsApi(request, await flowsApi.generateFlowViaApi(flow.fileName, flow.flowId), baseURL);
        for (let i = 0; i < executionCount; i++) {
            await executionsApi.generateExecutionViaApi();
        }

        await logsPage.goto();
        await logsPage.setFilterByNamespace(shared.namespace);
        await logsPage.setFilterByFlowId(executionsApi.flowId);

        await use(executionsApi);

        await executionsApi.removeExecutionsViaApi();
    },
    logsApi: async ({request, baseURL}, use) => {
        const logsApi = new LogsApi(request, baseURL);
        await use(logsApi);
    },
    logsPage: async ({page}, use) => {
        const logsPage = new LogsPage(page);
        await use(logsPage);
    }
});

export {expect} from "@playwright/test";
