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
            if (value) {
                localStorage.setItem("envName", value);
            } else {
                localStorage.removeItem("envName");
            }

            state.envName = value;
        },
        setEnvColor(state, value) {
            if (value) {
                localStorage.setItem("envColor", value);
            } else {
                localStorage.removeItem("envColor");
            }

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