export default {
    namespaced: true,
    state: {
        topNavbar: undefined,
        envName: undefined,
        envColor: undefined
    },
    actions: {},
    mutations: {
        setTopNavbar(state, value) {
            state.topNavbar = value
        },
        setEnvName(state, value) {
            localStorage.setItem("envName", value);
            state.envName = value;
        },
        setEnvColor(state, value) {
            localStorage.setItem("envColor", value);
            state.envColor = value;
        }
    },
    getters: {
        envName(state) {
            if (!state.envName) {
                state.envName = localStorage.getItem("envName");
            }
            return state.envName;
        },
        envColor(state) {
            if (!state.envColor) {
                state.envColor = localStorage.getItem("envColor");
            }
            return state.envColor;
        }
    }
}