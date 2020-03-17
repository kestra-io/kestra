export default {
    namespaced: true,
    state: {
        loading: false,
        errorMessage: undefined
    },
    actions: {
        showErrorMessage({commit}, message) {
            commit('setErrorMessage', message)
        }
    },
    mutations: {
        setLoading(state, value) {
            state.loading = value
        },
        setErrorMessage(state, message) {
            state.errorMessage = message
        }
    },
    getters: {}
}