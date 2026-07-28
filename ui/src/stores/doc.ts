import axios from "axios"
import {defineStore} from "pinia"
import {computed, ref} from "vue"
import {API_URL} from "./api"

const PATH_PLACEHOLDER = "{path}"

interface DocMetadata {
    [key: string]: any;
}

interface FetchResourceResponse {
    content: any;
    metadata?: DocMetadata;
}

interface SearchResult {
    parsedUrl: string;
    title: string;
    preview: string;
}

export const useDocStore = defineStore("doc", () => {
    const pageMetadata = ref<DocMetadata | undefined>(undefined)
    const resourceUrlTemplate = ref<string | undefined>(undefined)
    const docPath = ref<string | undefined>(undefined)
    const docId = ref<string | undefined>(undefined)

    const resourceUrl = computed(() => (path?: string, domain: string = "/docs"): string | undefined => {
        if (resourceUrlTemplate.value) {
            let resourcePath = ""
            if (path !== undefined) {
                resourcePath = path.startsWith("/") ? path : `/${path}`
            }
            if (!domain.startsWith("/")) {
                domain = "/" + domain
            }
            return resourceUrlTemplate.value.replace(PATH_PLACEHOLDER, domain + resourcePath)
        }
        return undefined
    })

    async function children(prefix?: string): Promise<any> {
        const url = resourceUrl.value(prefix)
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await axios.get(url + "/children")
        return response.data
    }

    async function fetchResource(path: string): Promise<FetchResourceResponse> {
        const url = resourceUrl.value(path)
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await axios.get(url)

        let metadata = response.headers["x-kestra-metadata"]
        if (metadata !== undefined) {
            metadata = JSON.parse(metadata)
        }

        return {
            content: response.data,
            metadata,
        }
    }

    async function fetchDocId(id: string): Promise<FetchResourceResponse> {
        const url = resourceUrl.value()
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await axios.get(`${url}/doc/${id}`)

        let metadata = response.headers["x-kestra-metadata"]
        if (metadata !== undefined) {
            metadata = JSON.parse(metadata)
        }

        docPath.value = metadata.parsedUrl

        return {
            content: response.data,
            metadata,
        }
    }

    async function search({q, scoredSearch = false}: {q: string; scoredSearch?: boolean}): Promise<any> {
        if (scoredSearch) {
            const url = resourceUrl.value(undefined, "search")
            if (!url) throw new Error("Resource URL template not initialized")

            const response = await axios.get(`${url}?q=${q}&type=DOCS`)
            return response.data.results.map(({url: itemUrl, title, highlights}: {url: string; title: string; highlights?: string[]}): SearchResult => ({
                parsedUrl: itemUrl,
                title,
                // highlights are HTML snippets (with <mark>/<br/> tags); strip them for a plain-text preview
                preview: (highlights?.[0] ?? "").replace(/<br\s*\/?>/gi, " ").replace(/<[^>]+>/g, "").replace(/\s+/g, " ").trim(),
            }))
        }

        const url = resourceUrl.value()
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await axios.get(`${url}/search?q=${q}`)
        return response.data
    }

    function initResourceUrlTemplate(version: string) {
        resourceUrlTemplate.value = `${API_URL}/v1${PATH_PLACEHOLDER}/versions/${version}`
    }

    return {
        pageMetadata,
        resourceUrlTemplate,
        docPath,
        docId,
        resourceUrl,
        children,
        fetchResource,
        fetchDocId,
        search,
        initResourceUrlTemplate,
    }
})
