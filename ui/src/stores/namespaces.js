import {apiUrl} from "override/utils/route";
import Utils from "../utils/utils";

export default {
    namespaced: true,
    state: {
        datatypeNamespaces: undefined,
    },

    actions: {
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
        exportFiles({_commit}, options) {
            this.$http.get(`${apiUrl(this)}/namespaces/${options.namespace}/files/export`)
                .then(response => Utils.downloadUrl(response.request.responseURL, options.namespace + "_files.zip"));
        }
    },
    mutations: {
        setDatatypeNamespaces(state, datatypeNamespaces) {
            state.datatypeNamespaces = datatypeNamespaces
        }
    },
    getters: {}
}
