import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";

function base(namespace) {
    return `${apiUrl(this)}/namespaces/${namespace}`;
}
const HEADERS = {headers: {"Content-Type": "multipart/form-data"}};

const slashPrefix = (path) => (path.startsWith("/") ? path : `/${path}`);
const safePath = (path) => encodeURIComponent(path).replace(/%2C|%2F/g, "/");

export default {
    namespaced: true,
    state: {
        datatypeNamespaces: undefined,
    },
    actions: {
        // Create a directory
        async createDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files/directory?path=${slashPrefix(payload.path)}`;
            await this.$http.post(URL);
        },

        // List directory content
        async readDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files/directory${payload.path ? `?path=${slashPrefix(safePath(payload.path))}` : ""}`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Create a file
        async createFile(_, payload) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${base.call(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            await this.$http.post(URL, DATA, HEADERS);
        },

        // Get namespace file content
        async readFile(_, payload) {
            if(!payload.path) return;

            const URL = `${base.call(this, payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`;
            const request = await this.$http.get(URL, {transformResponse: response => response, responseType: "json"})

            return request.data ?? [];
        },

        // Search for namespace files
        async searchFiles(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files/search?q=${payload.query}`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Import a file or directory
        async importFileDirectory(_, payload) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${base.call(this, payload.namespace)}/files?path=${slashPrefix(safePath(payload.path))}`;
            await this.$http.post(URL, DATA, HEADERS);
        },

        // Move a file or directory
        async moveFileDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            await this.$http.put(URL);
        },

        // Rename a file or directory
        async renameFileDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            await this.$http.put(URL);
        },

        // Delete a file or directory
        async deleteFileDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            await this.$http.delete(URL);
        },

        // Export namespace files as a ZIP
        async exportFileDirectory(_, payload) {
            const URL = `${base.call(this, payload.namespace)}/files/export`;
            const request = await this.$http.get(URL);

            const name = payload.namespace + "_files.zip";
            Utils.downloadUrl(request.request.responseURL, name);
        },

        loadNamespacesForDatatype({commit}, options) {
            return this.$http
                .get(`${apiUrl(this)}/${options.dataType}s/distinct-namespaces`)
                .then((response) => {
                    commit("setDatatypeNamespaces", response.data);
                });
        }
    },
    mutations: {
        setDatatypeNamespaces(state, datatypeNamespaces) {
            state.datatypeNamespaces = datatypeNamespaces;
        },
    },
};
