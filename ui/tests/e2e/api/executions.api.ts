import {APIRequestContext} from "playwright/test";
import {shared} from "../fixtures/shared";
import {BaseApi} from "./base.api";

export class ExecutionsApi extends BaseApi {
    private readonly executionIds: string[] = [];

    constructor(public readonly requests: APIRequestContext, public readonly flowId: string, protected readonly baseURL: string | undefined) {
        super(requests, baseURL);
    }

    async generateExecutionViaApi(labels: [string, string][] = []) {
        const formData = new FormData();
        formData.append("INPUT_A", "test");

        const params = new URLSearchParams();
        labels.forEach((tuple) => {
            params.append("labels", `${tuple[0]}:${tuple[1]}`);
        });

        const response = this.request.post(`${this.apiUrl}/executions/${shared.namespace}/${this.flowId}`, {
            headers: {
                "Accept": "application/json",
                "Authorization": ExecutionsApi.AUTH
            },
            params,
            multipart: formData
        });

        const status = (await response).status();

        if (status !== 200) {
            throw new Error(`Execution creation failed with HTTP ${status}: ${await (await response).text()}`);
        }

        const responseJson = await (await response).json();

        this.executionIds.push(responseJson["id"]);
    }

    async removeExecutionsViaApi() {
        for (const executionId of this.executionIds) {
            const params = new URLSearchParams();
            params.append("deleteLogs", "true");
            params.append("deleteMetric", "true");
            params.append("deleteStorage", "true");

            const status = (await this.request.delete(`${this.apiUrl}/executions/${executionId}`, {
                headers: {
                    "Authorization": ExecutionsApi.AUTH
                },
                params
            })).status();

            if (status !== 204) {
                throw new Error(`Deletion of execution ${executionId} failed with HTTP ${status}`);
            }
        };
    }

}