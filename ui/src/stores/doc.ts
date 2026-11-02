import {defineStore} from "pinia"
import {computed, ref} from "vue"
import {API_URL} from "./api"
import {publicFetch, publicFetchJson} from "../utils/publicApi"

const PATH_PLACEHOLDER = "{path}"

export interface DocMetadata {
    parsedUrl?: string;
    title: string;
    description?: string;
    release?: string;
    isHomepage?: boolean;
    hideSidebar?: boolean;
    sidebarTitle?: string;
    [key: string]: unknown;
}

interface FetchResourceResponse {
    content: string;
    metadata?: DocMetadata;
}

interface SearchResult {
    parsedUrl: string;
    title: string;
    preview: string;
}

/**
 * recursively removes HTML tags from a string.
 * the recursive nature is to avoid issues with nested tags 
 * and to ensure all tags are removed.
 * @see https://github.com/kestra-io/kestra/security/code-scanning/88
 * @param input The input string containing HTML.
 * @returns The input string with all HTML tags removed.
 */
function recursivelyRemoveHtmlTags(input: string): string {
    while (/<[^>]+>/.test(input)) {
        input = input.replace(/<[^>]+>/g, "")
    }
    return input
}

/**
 * Sanitize a preview string by removing HTML tags and replacing <br> with spaces.
 * @param preview The preview string containing HTML.
 * @returns The sanitized plain-text preview string.
 */
function sanitizePreview(preview: string): string {
    return recursivelyRemoveHtmlTags(preview.replace(/<br\s*\/?>/gi, " "))
    .replace(/\s+/g, " ").trim()
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

    async function children(prefix?: string): Promise<Record<string, DocMetadata>> {
        const url = resourceUrl.value(prefix)
        if (!url) throw new Error("Resource URL template not initialized")

        return publicFetchJson<Record<string, DocMetadata>>(url + "/children")
    }

    async function fetchResource(path: string): Promise<FetchResourceResponse> {
        const url = resourceUrl.value(path)
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await publicFetch(url)

        const metadataHeader = response.headers.get("x-kestra-metadata")
        const metadata = metadataHeader !== null
            ? JSON.parse(metadataHeader) as DocMetadata
            : undefined

        return {
            content: await response.text(),
            metadata,
        }
    }

    async function fetchDocId(id: string): Promise<FetchResourceResponse> {
        const url = resourceUrl.value()
        if (!url) throw new Error("Resource URL template not initialized")

        const response = await publicFetch(`${url}/doc/${id}`)

        const metadataHeader = response.headers.get("x-kestra-metadata")
        const metadata = metadataHeader !== null
            ? JSON.parse(metadataHeader) as DocMetadata
            : undefined

        if (id === docId.value) {
            docPath.value = metadata?.parsedUrl
        }

        return {
            content: await response.text(),
            metadata,
        }
    }

    async function search({q, scoredSearch = false}: {q: string; scoredSearch?: boolean}): Promise<SearchResult[]> {
        if (scoredSearch) {
            const url = resourceUrl.value(undefined, "search")
            if (!url) throw new Error("Resource URL template not initialized")

            const response = await publicFetchJson<{results: Array<{url: string; title: string; highlights?: string[]}>}>(url, {params: {q, type: "DOCS"}})
            return response.results.map(({url: itemUrl, title, highlights}: {url: string; title: string; highlights?: string[]}): SearchResult => ({
                parsedUrl: itemUrl,
                title,
                preview: sanitizePreview(highlights?.[0] ?? ""),
            }))
        }

        const url = resourceUrl.value()
        if (!url) throw new Error("Resource URL template not initialized")

        return publicFetchJson<SearchResult[]>(`${url}/search`, {params: {q}})
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
