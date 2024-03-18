export default {
    namespaced: true,
    state: {
        lastFilters: undefined
    },
    mutations: {
        setSavedFilters(state, value) {
            localStorage.setItem(value.storageKey, JSON.stringify(value.filters));
        }
    },
    getters: {
        savedFilters() {
            return (storageKey) => {
                return JSON.parse(localStorage.getItem(storageKey)) ?? {}
            }
        }
    }
}