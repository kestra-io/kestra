import YamlUtils from "../utils/yamlUtils";
import Utils from "../utils/utils";

export default {
    namespaced: true,
    state: {
        templates: undefined,
        template: undefined,
        total: 0,
    },

    actions: {
        findTemplates({commit}, options) {
            const sortString = options.sort ? `?sort=${options.sort}` : ""
            delete options.sort
            return this.$http.get(`/api/v1/templates/search${sortString}`, {
                params: options
            }).then(response => {
                commit("setTemplates", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        loadTemplate({commit}, options) {
            return this.$http.get(`/api/v1/templates/${options.namespace}/${options.id}`).then(response => {
                if (response.data.exception) {
                    commit("core/setMessage", {title: "Invalid source code", message: response.data.exception, variant: "danger"}, {root: true});
                    delete response.data.exception;
                    commit("setTemplate", JSON.parse(response.data.source));
                } else {
                    commit("setTemplate", response.data)
                }

                return response.data;
            })
        },
        saveTemplate({commit}, options) {
            const template = YamlUtils.parse(options.template)
            return this.$http.put(`/api/v1/templates/${template.namespace}/${template.id}`, template).then(response => {
                if (response.status >= 300) {
                    return Promise.reject(new Error("Server error on template save"))
                } else {
                    commit("setTemplate", response.data)

                    return response.data;
                }
            })
        },
        createTemplate({commit}, options) {
            return this.$http.post("/api/v1/templates", YamlUtils.parse(options.template)).then(response => {
                commit("setTemplate", response.data)

                return response.data;
            })
        },
        deleteTemplate({commit}, template) {
            return this.$http.delete(`/api/v1/templates/${template.namespace}/${template.id}`).then(() => {
                commit("setTemplate", null)
            })
        },
        exportTemplateByIds(_, options) {
            return this.$http.post("/api/v1/templates/export/by-ids", options.ids, {responseType: "blob"})
                .then(response => {
                    const blob = new Blob([response.data], {type: "application/octet-stream"});
                    const url = window.URL.createObjectURL(blob)
                    Utils.downloadUrl(url, "templates.zip");
                });
        },
        exportTemplateByQuery(_, options) {
            return this.$http.get("/api/v1/templates/export/by-query", {params: options})
                .then(response => {
                    Utils.downloadUrl(response.request.responseURL, "templates.zip");
                });
        },
        importTemplates(_, options) {
            return this.$http.post("/api/v1/templates/import", options, {headers: {"Content-Type": "multipart/form-data"}})
        }
    },
    mutations: {
        setTemplates(state, templates) {
            state.templates = templates
        },
        setTemplate(state, template) {
            state.template = template;
        },
        setTotal(state, total) {
            state.total = total
        },
    },
}
