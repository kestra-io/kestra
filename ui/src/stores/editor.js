export default {
    namespaced: true,
    state: {
        reference: undefined,
        explorerVisible: true,
    },
    mutations: {
        setReference(state, reference) {
            state.reference = reference;
        },
        toggleExplorerVisibility(state) {
            state.explorerVisible = !state.explorerVisible;
        },
    },
};
