import {apiUrlWithoutTenants} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        configs: undefined,
    },

    actions: {
        loadConfigs({commit}) {
            return this.$http.get(`${apiUrlWithoutTenants()}/configs`).then(response => {
                commit("setConfigs", response.data)

                return response.data;
            })
        },
    },
    mutations: {
        setConfigs(state, configs) {
            state.configs = configs
        }
    },
    getters: {
        configs(state) {
            return state.configs;
        }
    }
}
