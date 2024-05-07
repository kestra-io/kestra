import Utils from "../utils/utils";
import {apiUrl} from "override/utils/route";

const BASE = (namespace) => `${apiUrl(this)}/namespaces/${namespace}`;
const HEADERS = {headers: {"Content-Type": "multipart/form-data"}};

const NOTIFY = ({toast, status, action, name, type}) => {
    if (status === 200) toast.success(`${type} ${name} is ${action}.`);
    else toast.error("An unexpected error occurred.");
};

const slashPrefix = (path) => (path.startsWith("/") ? path : `/${path}`);

export default {
    namespaced: true,
    state: {
        datatypeNamespaces: undefined,
    },
    actions: {
        // Create a directory
        async createDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files/directory?path=${slashPrefix(payload.path)}`;
            const request = await this.$http.post(URL);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: "created",
                name: payload.name,
                type: "Directory",
            });
        },

        // List directory content
        async readDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files/directory${payload.path ? `?path=${slashPrefix(payload.path)}` : ""}`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Create a file
        async createFile(_, payload) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${BASE(payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            const request = await this.$http.post(URL, DATA, HEADERS);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: payload.creation ? "created" : "updated",
                name: payload.name ? payload.name : "content",
                type: "File",
            });
        },

        // Get namespace file content
        async readFile(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            const request = await this.$http.get(URL);

            return request.data ?? [];
        },

        // Import a file or directory
        async importFileDirectory(_, payload) {
            const DATA = new FormData();
            const BLOB = new Blob([payload.content], {type: "text/plain"});
            DATA.append("fileContent", BLOB);

            const URL = `${BASE(payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            await this.$http.post(URL, DATA, HEADERS);
        },

        // Move a file or directory
        async moveFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            const request = await this.$http.put(URL);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: "moved",
                name: payload.new,
                type: payload.type,
            });
        },

        // Rename a file or directory
        async renameFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?from=${slashPrefix(payload.old)}&to=${slashPrefix(payload.new)}`;
            const request = await this.$http.put(URL);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: "renamed",
                name: payload.new,
                type: payload.type,
            });
        },

        // Delete a file or directory
        async deleteFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files?path=${slashPrefix(payload.path)}`;
            const request = await this.$http.delete(URL);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: "deleted",
                name: payload.name,
                type: payload.type,
            });
        },

        // Export namespace files as a ZIP
        async exportFileDirectory(_, payload) {
            const URL = `${BASE(payload.namespace)}/files/export`;
            const request = await this.$http.get(URL);

            const name = payload.namespace + "_files.zip";
            Utils.downloadUrl(request.request.responseURL, name);

            NOTIFY({
                toast: this.$toast,
                status: request.status,
                action: "exported",
                name,
                type: "File",
            });
        },

        loadNamespacesForDatatype({commit}, options) {
            return this.$http
                .get(`${apiUrl(this)}/${options.dataType}s/distinct-namespaces`)
                .then((response) => {
                    commit("setDatatypeNamespaces", response.data);
                });
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
                `${apiUrl(this)}/namespaces/${options.namespace}/files?path=${slashPrefix(path)}`,
                formData,
                HEADERS,
            );
        }
    },
    mutations: {
        setDatatypeNamespaces(state, datatypeNamespaces) {
            state.datatypeNamespaces = datatypeNamespaces;
        },
    },
};
