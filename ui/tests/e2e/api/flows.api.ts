import {APIRequestContext} from "playwright/test";
import {BaseApi} from "./base.api";
import {shared} from "../fixtures/shared";
import {v4 as uuid} from "uuid";
import {fileURLToPath} from "url";
import {dirname} from "path";
import fs from "fs";
import path from "path";

export class FlowsApi extends BaseApi {
    private readonly flowIds: string[] = [];

    constructor(public readonly requests: APIRequestContext, protected readonly baseURL: string | undefined) {
        super(requests, baseURL);
    }

    async generateFlowViaApi(fileName: string, fileFlowId: string) {
        const flowId = `test-flow-${uuid()}`;

        // Create flow via API
        const response = this.request.post(`${this.apiUrl}/flows`, {
            headers: {
                "Content-Type": "application/x-yaml",
                "Accept": "application/json",
                "Authorization": FlowsApi.AUTH
            },
            data: this.getFlowYaml(fileName, fileFlowId, flowId)
        });

        const status = (await response).status();

        if (status !== 200) {
            throw new Error(`Flow creation failed with HTTP ${status}`);
        }

        this.flowIds.push(flowId);

        return flowId;
    }

    async removeFlowsViaApi() {
        for(const flowId of this.flowIds) {
            const status = (await this.request.delete(`${this.apiUrl}/flows/${shared.namespace}/${flowId}`, {
                headers: {
                    "Authorization": FlowsApi.AUTH
                }
            })).status();

            if (status !== 204) {
                throw new Error(`Deletion of flow ${flowId} failed with HTTP ${status}`);
            }
        };
    }

    protected getFlowYaml(fileName: string, fileFlowId: string, desiredFlowId: string): string {
        const __filename = fileURLToPath(import.meta.url);
        const __dirname = dirname(__filename);
        const flowYaml = fs.readFileSync(
            path.resolve(__dirname, `../fixtures/flows/${fileName}`),
            "utf-8"
        );

        return flowYaml.replace(fileFlowId, desiredFlowId);
    }
}
