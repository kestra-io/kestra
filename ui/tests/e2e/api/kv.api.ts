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

    async setKvViaApi(key: string, value: string, ttl?: string) {
        const response = await this.request.put(`${this.apiUrl}/namespaces/${shared.namespace}/kv/${key}`, {
            headers: {
                "Content-Type": "text/plain",
                "Authorization": KvApi.AUTH,
                ...(ttl === undefined ? {} : {ttl}),
            },
            data: value,
        })

        if (response.status() !== 200) {
            throw new Error(`Writing KV ${key} failed with HTTP ${response.status()}`)
        }
    }

    async getExpirationDateViaApi(key: string): Promise<string | undefined> {
        const response = await this.request.get(`${this.apiUrl}/kv?page=1&size=100&filters[namespace][EQUALS]=${shared.namespace}`, {
            headers: {
                "Accept": "application/json",
                "Authorization": KvApi.AUTH,
            },
        })

        if (response.status() !== 200) {
            throw new Error(`Listing KVs failed with HTTP ${response.status()}`)
        }

        const {results} = await response.json()

        return results.find((entry: {key: string}) => entry.key === key)?.expirationDate
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
