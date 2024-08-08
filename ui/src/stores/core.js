import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        message: undefined,
        error: undefined,
        unsavedChange: false,
        guidedProperties: {
            tourStarted: false,
            manuallyContinue: false,
            template: undefined,
        },
        monacoYamlConfigured: false,
        autocompletionSource: undefined,
        tutorialFlows: []
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
        readTutorialFlows({commit}) {
            return this.$http.get(`${apiUrl(this)}/flows/tutorial`).then((response) => commit("setTutorialFlows", response.data))
        },
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
            state.guidedProperties = {...state.guidedProperties, ...guidedProperties}
        },
        setMonacoYamlConfigured(state, monacoYamlConfigured) {
            state.monacoYamlConfigured = monacoYamlConfigured
        },
        setAutocompletionSource(state, autocompletionSource) {
            state.autocompletionSource = autocompletionSource
        },
        setTutorialFlows(state, flows) {
            state.tutorialFlows = flows
        },
    },
    getters: {
        unsavedChange(state) {
            return state.unsavedChange;
        },
        guidedProperties(state) {
            return state.guidedProperties;
        },
    }
}
