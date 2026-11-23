import {APIRequestContext} from "playwright/test"
import {BaseApi} from "./base.api"

export class NamespaceFilesApi extends BaseApi {
    constructor(request: APIRequestContext, baseURL: string | undefined, public readonly namespace: string) {
        super(request, baseURL)
    }

    private url(suffix = "") {
        return `${this.apiUrl}/namespaces/${this.namespace}/files${suffix}`
    }

    async writeFile(path: string, content: string) {
        const response = await this.request.post(this.url(), {
            headers: {"Authorization": NamespaceFilesApi.AUTH},
            params: {path: `/${path}`},
            multipart: {fileContent: {name: path.split("/").pop()!, mimeType: "text/plain", buffer: Buffer.from(content)}},
        })

        if (response.status() !== 200) {
            throw new Error(`Writing namespace file ${path} failed with HTTP ${response.status()}`)
        }
    }

    /** The stored bytes decoded as UTF-8, so a test can compare them exactly with what was typed. */
    async readFile(path: string): Promise<string> {
        const response = await this.request.get(this.url(), {
            headers: {"Authorization": NamespaceFilesApi.AUTH},
            params: {path: `/${path}`},
        })

        if (response.status() !== 200) {
            throw new Error(`Reading namespace file ${path} failed with HTTP ${response.status()}`)
        }

        return (await response.body()).toString("utf-8")
    }

    /** Every save writes a revision, so the count tells a test whether a file was written at all. */
    async revisionCount(path: string): Promise<number> {
        const response = await this.request.get(this.url("/revisions"), {
            headers: {"Authorization": NamespaceFilesApi.AUTH},
            params: {path: `/${path}`},
        })

        if (response.status() !== 200) {
            throw new Error(`Listing revisions of ${path} failed with HTTP ${response.status()}`)
        }

        return (await response.json()).length
    }

    async removeAllViaApi() {
        const response = await this.request.delete(this.url(), {
            headers: {"Authorization": NamespaceFilesApi.AUTH},
            params: {path: "/"},
        })

        if (response.status() !== 200 && response.status() !== 404) {
            throw new Error(`Deleting the files of ${this.namespace} failed with HTTP ${response.status()}`)
        }
    }
}
