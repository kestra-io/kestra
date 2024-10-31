export default {
    namespaced: true,
    state: {
        title: "Kestra",
        breadcrumb: [],
    },

    mutations: {
        setTitle(state, title) {
            state.title = title
        },
        setBreadcrumb(state, breadcrumb) {
            state.breadcrumb = breadcrumb
        },
    }
}
