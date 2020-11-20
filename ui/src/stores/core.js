export default {
    namespaced: true,
    state: {
        message: undefined
    },
    actions: {
        showMessage({commit}, message) {
            commit("setMessage", message)
        }
    },
    mutations: {
        setMessage(state, message) {
            state.message = message
        }
    },
    getters: {}
}
