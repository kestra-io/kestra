export default {
    namespaced: true,
    state: {
        topNavbar: undefined
    },
    actions: {},
    mutations: {
        setTopNavbar(state, value) {
            console.log('set', value)
            state.topNavbar = value
        }
    },
    getters: {}
}