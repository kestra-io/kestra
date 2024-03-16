export default {
    namespaced: true,
    state: {
        savedFilters: undefined
    },
    mutations: {
        setSavedFilters(state, value) {
            state.filters = (value);
            localStorage.setItem("savedFilters", JSON.stringify(state.filters));
        }
    },
    getters: {
        savedFilters(state) {
            if (!state.savedFilters) {
                state.savedFilters = JSON.parse(localStorage.getItem("savedFilters")) ?? {};
            }
            return state.savedFilters;
        }
    }
}