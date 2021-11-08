export default {
    namespaced: true,
    state: {
        themes: undefined,
        theme: undefined,
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
        setThemes(state, themes) {
            state.themes = themes
        },
        setTheme(state, theme) {
            state.theme = theme
        },
        setMessage(state, message) {
            state.message = message
        },
        setError(state, error) {
            state.error = error
        }
    },
    getters: {}
}
