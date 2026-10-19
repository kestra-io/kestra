import {BaseApi} from "./base.api"
import {shared} from "../fixtures/shared"
import {v4 as uuid} from "uuid"
import {fileURLToPath} from "url"
import {dirname} from "path"
import fs from "fs"
import path from "path"

export type StoredFlow = {
    id: string;
    namespace: string;
    source: string;
    revision: number;
}

export class FlowsApi extends BaseApi {
    private readonly flows: {namespace: string; id: string}[] = []

    async generateFlowViaApi(fileName: string, fileFlowId: string) {
        const flowId = `test-flow-${uuid()}`

        // Create flow via API
        const response = this.request.post(`${this.apiUrl}/flows`, {
            headers: {
                "Content-Type": "application/x-yaml",
                "Accept": "application/json",
                "Authorization": FlowsApi.AUTH,
            },
            data: this.getFlowYaml(fileName, fileFlowId, flowId),
        })

        const status = (await response).status()

        if (status !== 200) {
            throw new Error(`Flow creation failed with HTTP ${status}`)
        }

        this.flows.push({namespace: shared.namespace, id: flowId})

        return flowId
    }

    async createFlowViaApi(source: string) {
        const response = await this.request.post(`${this.apiUrl}/flows`, {
            headers: {
                "Content-Type": "application/x-yaml",
                "Accept": "application/json",
                "Authorization": FlowsApi.AUTH,
            },
            data: source,
        })

        if (response.status() !== 200) {
            throw new Error(`Flow creation failed with HTTP ${response.status()}: ${await response.text()}`)
        }

        const {namespace, id}: StoredFlow = await response.json()
        this.flows.push({namespace, id})
    }

    async getFlowViaApi(namespace: string, flowId: string): Promise<StoredFlow> {
        const response = await this.request.get(`${this.apiUrl}/flows/${namespace}/${flowId}`, {
            headers: {
                "Accept": "application/json",
                "Authorization": FlowsApi.AUTH,
            },
            params: {source: "true"},
        })

        if (response.status() !== 200) {
            throw new Error(`Reading flow ${namespace}.${flowId} failed with HTTP ${response.status()}`)
        }

        return response.json()
    }

    async removeFlowsViaApi() {
        for(const {namespace, id: flowId} of this.flows) {
            const status = (await this.request.delete(`${this.apiUrl}/flows/${namespace}/${flowId}`, {
                headers: {
                    "Authorization": FlowsApi.AUTH,
                },
            })).status()

            if (status !== 204) {
                throw new Error(`Deletion of flow ${flowId} failed with HTTP ${status}`)
            }
        };
    }

    protected getFlowYaml(fileName: string, fileFlowId: string, desiredFlowId: string): string {
        const __filename = fileURLToPath(import.meta.url)
        const __dirname = dirname(__filename)
        const flowYaml = fs.readFileSync(
            path.resolve(__dirname, `../fixtures/flows/${fileName}`),
            "utf-8",
        )

        return flowYaml.replace(fileFlowId, desiredFlowId)
    }
}
