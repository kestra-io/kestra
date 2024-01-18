import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils";

export default {
    namespaced: true,
    state: {
        namespaces: undefined,
    },

    actions: {
        loadNamespaces({commit}, options) {
            return this.$http.get(`${apiUrl(this)}/${options.dataType}s/distinct-namespaces`).then(response => {
                commit("setNamespaces", response.data)
            })
        },
        importFile({commit}, options) {
            const file = options.file;
            const formData = new FormData();
            formData.append("fileContent", file);

            let path;
            if((file.webkitRelativePath ?? "") === "") {
                path = file.name;
            } else {
                path = file.webkitRelativePath.split("/").slice(1).join("/");
            }
            path = path.replace(" ", "_");

            return this.$http.post(
                `${apiUrl(this)}/namespaces/${options.namespace}/files?path=/${path}`,
                formData,
                {headers: {"Content-Type": "multipart/form-data"}}
            );
        },
        exportFiles({commit}, options) {
            this.$http.get(`${apiUrl(this)}/namespaces/${options.namespace}/files/export`)
                .then(response => Utils.downloadUrl(response.request.responseURL, options.namespace + "_files.zip"));
        }
    },
    mutations: {
        setNamespaces(state, namespaces) {
            state.namespaces = namespaces
        }
    },
    getters: {}
}
