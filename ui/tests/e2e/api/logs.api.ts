import {APIRequestContext} from "playwright/test";
import {BaseApi} from "./base.api";

export class LogsApi extends BaseApi {
    constructor(public readonly requests: APIRequestContext, protected readonly baseURL: string | undefined) {
        super(requests, baseURL);
    }

    async countLogsForFlow(namespace: string, flowId: string, minLevel: string = "INFO"): Promise<number> {
        const response = await this.request.get(`${this.apiUrl}/logs/search`, {
            headers: {
                "Authorization": LogsApi.AUTH
            },
            params: {
                page: "1",
                size: "1",
                minLevel,
                "filters[namespace][EQUALS]": namespace,
                "filters[flowId][EQUALS]": flowId
            }
        });

        const status = response.status();
        if (status !== 200) {
            throw new Error(`Logs search failed with HTTP ${status}: ${await response.text()}`);
        }

        const body = await response.json();
        return body.total ?? 0;
    }

    async removeLogsForFlow(namespace: string, flowId: string): Promise<void> {
        const status = (await this.request.delete(`${this.apiUrl}/logs/${namespace}/${flowId}`, {
            headers: {
                "Authorization": LogsApi.AUTH
            }
        })).status();

        if (status !== 200 && status !== 204) {
            throw new Error(`Logs cleanup for ${namespace}/${flowId} failed with HTTP ${status}`);
        }
    }
}
