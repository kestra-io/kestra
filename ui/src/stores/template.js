import Vue from "vue"
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
            return Vue.axios.get(`/api/v1/templates/search${sortString}`, {
                params: options
            }).then(response => {
                commit("setTemplates", response.data.results)
                commit("setTotal", response.data.total)

                return response.data;
            })
        },
        loadTemplate({commit}, options) {
            return Vue.axios.get(`/api/v1/templates/${options.namespace}/${options.id}`).then(response => {
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
            return Vue.axios.put(`/api/v1/templates/${options.template.namespace}/${options.template.id}`, options.template).then(response => {
                if (response.status >= 300) {
                    return Promise.reject(new Error("Server error on template save"))
                } else {
                    commit("setTemplate", response.data)

                    return response.data;
                }
            })
        },
        createTemplate({commit}, options) {
            return Vue.axios.post("/api/v1/templates", options.template).then(response => {
                commit("setTemplate", response.data)

                return response.data;
            })
        },
        deleteTemplate({commit}, template) {
            return Vue.axios.delete(`/api/v1/templates/${template.namespace}/${template.id}`).then(() => {
                commit("setTemplate", null)
            })
        },
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
