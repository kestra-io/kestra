export default {
    namespaced: true,
    state: {
        errorMessage: undefined
    },
    actions: {
        showErrorMessage({commit}, message) {
            commit("setErrorMessage", message)
        }
    },
    mutations: {
        setErrorMessage(state, message) {
            state.errorMessage = message
        }
    },
    getters: {}
}
