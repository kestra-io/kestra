import axios from "axios";
import {API_URL} from "./api.js";

const PATH_PLACEHOLDER = "{path}";

export default {
    namespaced: true,
    state: {
        pageMetadata: undefined,
        resourceUrlTemplate: undefined,
        appResourceUrlTemplate: undefined,
        docPath: undefined,
        docId: undefined
    },
    actions: {
        async children({getters}, prefix) {
            return axios.get(getters["resourceUrl"](prefix) + "/children")
                .then(response => response.data);
        },
        async fetchResource({getters}, path) {
            return axios.get(getters["resourceUrl"](path))
                .then(response => {
                    let metadata = response.headers["x-kestra-metadata"];
                    if (metadata !== undefined) {
                        metadata = JSON.parse(metadata);
                    }
                    return {
                        content: response.data,
                        metadata
                    }
                });
        },
        async fetchDocId({getters}, docId) {
            const url = getters["resourceUrl"]()
            const response = await axios.get(`${url}/doc/${docId}`)

            let metadata = response.headers["x-kestra-metadata"];
            if (metadata !== undefined) {
                metadata = JSON.parse(metadata);
            }

            return {
                content: response.data,
                metadata
            }
        },
        async search({getters}, {q, scoredSearch = false}) {
            if (scoredSearch) {
                return axios.get(`${getters["resourceUrl"](undefined, "search")}?q=${q}&type=DOCS`)
                    .then(({data}) => data.results.map(({url, title}) => ({
                        parsedUrl: url,
                        title
                    })));
            }

            return axios.get(`${getters["resourceUrl"]()}/search?q=${q}`)
                .then(response => response.data)
        },
        initResourceUrlTemplate({commit}, version) {
            commit("setResourceUrlTemplate", `${API_URL}/v1${PATH_PLACEHOLDER}/versions/${version}`);
        }
    },
    mutations: {
        setPageMetadata(state, metadata) {
            state.pageMetadata = metadata
        },
        setResourceUrlTemplate(state, resourceUrlTemplate) {
            state.resourceUrlTemplate = resourceUrlTemplate;
        },
        setDocPath(state, newPath) {
            state.docPath = newPath;
        },
        setDocId(state, docId) {
            state.docId = docId;
        }
    },
    getters: {
        pageMetadata: (state) => {
            return state.pageMetadata;
        },
        resourceUrl: (state) => (path, domain = "/docs") => {
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
        },
        docPath: (state) => {
            return state.docPath;
        }
    }
}
