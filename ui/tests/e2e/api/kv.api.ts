import {BaseApi} from "./base.api"
import {shared} from "../fixtures/shared"

/** What `GET /namespaces/{namespace}/kv/{key}` answers, once the stored ION has been parsed. */
export type KvDetail = {
    type: string;
    value: any;
}

export class KvApi extends BaseApi {
    private readonly keys: string[] = []

    /** Records a key created through the UI so the spec can clean it up. */
    track(key: string) {
        this.keys.push(key)
        return key
    }

    async getKvViaApi(key: string): Promise<KvDetail> {
        const response = await this.request.get(`${this.apiUrl}/namespaces/${shared.namespace}/kv/${key}`, {
            headers: {
                "Accept": "application/json",
                "Authorization": KvApi.AUTH,
            },
        })

        if (response.status() !== 200) {
            throw new Error(`Reading KV ${key} failed with HTTP ${response.status()}`)
        }

        return response.json()
    }

    async removeKvsViaApi() {
        for (const key of this.keys) {
            const status = (await this.request.delete(`${this.apiUrl}/namespaces/${shared.namespace}/kv/${key}`, {
                headers: {
                    "Authorization": KvApi.AUTH,
                },
            })).status()

            if (status !== 200 && status !== 404) {
                throw new Error(`Deletion of KV ${key} failed with HTTP ${status}`)
            }
        }

        this.keys.length = 0
    }
}
