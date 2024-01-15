export default {
    namespaced: true,
    state: {
        message: undefined,
        error: undefined,
        unsavedChange: false,
        guidedProperties: {
            tourStarted: false,
            flowSource: undefined,
            saveFlow: false,
            executeFlow: false,
            validateInputs: false,
            monacoRange: undefined,
            monacoDisableRange: undefined,
        }
    },
    actions: {
        showMessage({commit}, message) {
            commit("setMessage", message)
        },
        showError({commit}, error) {
            commit("setError", error)
        },
        isUnsaved({commit}, unsavedChange) {
            commit("setUnsavedChange", unsavedChange)
        },
        storeRoute({commit}, route) {
            commit("setRoute", route)
        }
    },
    mutations: {
        setMessage(state, message) {
            state.message = message
        },
        setError(state, error) {
            state.error = error
        },
        setUnsavedChange(state, unsavedChange) {
            state.unsavedChange = unsavedChange
        },
        setGuidedProperties(state, guidedProperties) {
            state.guidedProperties = guidedProperties
        },
        setRoute(state, route) {
            state.route = route
        }
    },
    getters: {
        unsavedChange(state) {
            return state.unsavedChange;
        },
        guidedProperties(state) {
            return state.guidedProperties;
        },
        route(state) {
            return state.route;
        }
    }
}
