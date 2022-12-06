export default {
    namespaced: true,
    state: {
        message: undefined,
        error: undefined
    },
    actions: {
        showMessage({commit}, message) {
            commit("setMessage", message)
        },
        showError({commit}, error) {
            commit("setError", error)
        }
    },
    mutations: {
        setMessage(state, message) {
            state.message = message
        },
        setError(state, error) {
            state.error = error
        }
    },
    getters: {}
}
