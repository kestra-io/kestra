import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";

const BASE = (namespace) => `${apiUrl(this)}/namespaces/${namespace}`;
const HEADERS = {headers: {"Content-Type": "multipart/form-data"}};

export default {
    namespaced: true,
    state: {
        datatypeNamespaces: undefined,
    },
    actions: {
        // Create a directory
        async createDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files/directory?path=/${payload.path}`;
            await this.$http.post(URL);
        },

        // List directory content
        async readDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files/directory`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Create a file
        async createFile(_, payload) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${BASE(payload.namespace)}/files?path=/${payload.path}`;
            await this.$http.post(URL, DATA, HEADERS);
        },

        // Get namespace file content
        async readFile(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?path=/${payload.path}`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Move a file or directory
        async moveFileDirectory(_, payload) {
            console.log("moveFileDirectory", payload);
        },

        // Rename a file or directory
        async renameFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?from=/${payload.old}&to=/${payload.new}`;
            await this.$http.put(URL);
        },

        // Delete a file or directory
        async deleteFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?path=/${payload.path}`;
            await this.$http.delete(URL);
        },

        // Export namespace files as a ZIP
        async exportFileDirectory(_, payload) {
            console.log("exportFileDirectory", payload);
        },

        loadNamespacesForDatatype({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/${options.dataType}s/distinct-namespaces`).then(response => {
                commit("setDatatypeNamespaces", response.data)
            })
        },
        importFile({_commit}, options) {
            const file = options.file;
            const formData = new FormData();
            formData.append("fileContent", file);

            let path;
            if ((file.webkitRelativePath ?? "") === "") {
                path = file.name;
            } else {
                path = file.webkitRelativePath.split("/").slice(1).join("/");
            }
            path = path.replace(" ", "_");

            return this.$http.post(
                `${apiUrl(this)}/namespaces/${options.namespace}/files?path=/${path}`,
                formData,
                HEADERS
            );
        },
        exportFiles({_commit}, options) {
            this.$http
                .get(
                    `${apiUrl(this)}/namespaces/${options.namespace}/files/export`,
                )
                .then((response) =>
                    Utils.downloadUrl(
                        response.request.responseURL,
                        options.namespace + "_files.zip",
                    ),
                );
        },
    },
    mutations: {
        setDatatypeNamespaces(state, datatypeNamespaces) {
            state.datatypeNamespaces = datatypeNamespaces
        }
    }
}
