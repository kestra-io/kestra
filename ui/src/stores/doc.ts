import axios from "axios";
import {defineStore} from "pinia";
import {API_URL} from "./api";

const PATH_PLACEHOLDER = "{path}";

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
}

interface State {
    pageMetadata: DocMetadata | undefined;
    resourceUrlTemplate: string | undefined;
    appResourceUrlTemplate: string | undefined;
    docPath: string | undefined;
    docId: string | undefined;
}

export const useDocStore = defineStore("doc", {
    state: (): State => ({
        pageMetadata: undefined,
        resourceUrlTemplate: undefined,
        appResourceUrlTemplate: undefined,
        docPath: undefined,
        docId: undefined
    }),

    getters: {
        resourceUrl: (state) => (path?: string, domain: string = "/docs"): string | undefined => {
            if (state.resourceUrlTemplate) {
                let resourcePath = "";
                if (path !== undefined) {
                    resourcePath = path.startsWith("/") ? path : `/${path}`;
                }
                if (!domain.startsWith("/")) {
                    domain = "/" + domain;
                }
                return state.resourceUrlTemplate.replace(PATH_PLACEHOLDER, domain + resourcePath);
            }
            return undefined;
        }
    },

    actions: {
        async children(prefix?: string): Promise<any> {
            const url = this.resourceUrl(prefix);
            if (!url) throw new Error("Resource URL template not initialized");
            
            const response = await axios.get(url + "/children");
            return response.data;
        },

        async fetchResource(path: string): Promise<FetchResourceResponse> {
            const url = this.resourceUrl(path);
            if (!url) throw new Error("Resource URL template not initialized");
            
            const response = await axios.get(url);
            
            let metadata = response.headers["x-kestra-metadata"];
            if (metadata !== undefined) {
                metadata = JSON.parse(metadata);
            }
            
            return {
                content: response.data,
                metadata
            };
        },

        async fetchDocId(docId: string): Promise<FetchResourceResponse> {
            const url = this.resourceUrl();
            if (!url) throw new Error("Resource URL template not initialized");
            
            const response = await axios.get(`${url}/doc/${docId}`);

            let metadata = response.headers["x-kestra-metadata"];
            if (metadata !== undefined) {
                metadata = JSON.parse(metadata);
            }

            return {
                content: response.data,
                metadata
            };
        },

        async search({q, scoredSearch = false}: {q: string; scoredSearch?: boolean}): Promise<any> {
            if (scoredSearch) {
                const url = this.resourceUrl(undefined, "search");
                if (!url) throw new Error("Resource URL template not initialized");
                
                const response = await axios.get(`${url}?q=${q}&type=DOCS`);
                return response.data.results.map(({url, title}: {url: string; title: string}): SearchResult => ({
                    parsedUrl: url,
                    title
                }));
            }

            const url = this.resourceUrl();
            if (!url) throw new Error("Resource URL template not initialized");
            
            const response = await axios.get(`${url}/search?q=${q}`);
            return response.data;
        },

        initResourceUrlTemplate(version: string) {
            this.resourceUrlTemplate = `${API_URL}/v1${PATH_PLACEHOLDER}/versions/${version}`;
        }
    }
});
