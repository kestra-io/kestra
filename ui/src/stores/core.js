export default {
    namespaced: true,
    state: {
        loading: false,
    },
    actions: {

    },
    mutations: {
        setLoading(state, value) {
            state.loading = value
        }
    },
    getters: {}
}