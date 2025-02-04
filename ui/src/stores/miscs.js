import {apiUrl} from "override/utils/route";

export default {
    namespaced: true,
    state: {
        configs: undefined,
        contextInfoBarOpenTab: "",
    },
    actions: {
        loadConfigs({commit}) {
            return this.$http.get(`${apiUrl(this)}/configs`).then(response => {
                commit("setConfigs", response.data)

                return response.data;
            })
        },
        loadAllUsages() {
            return this.$http.get(`${apiUrl(this)}/usages/all`).then(response => {
                return response.data;
            })
        },
        async addBasicAuth({_commit, state}, options) {
            const email = options.username;

            await this.$http.post(`${apiUrl(this)}/basicAuth`, {
                uid: localStorage.getItem("uid"),
                username: email,
                password: options.password,
            });

            return this.dispatch("api/posthogEvents", {
                type: "ossauth",
                iid: state.configs.uuid,
                uid: localStorage.getItem("uid"),
                date: new Date().toISOString(),
                counter: 0,
                email: email
            });
        }
    },
    mutations: {
        setConfigs(state, configs) {
            state.configs = configs
        },
        setContextInfoBarOpenTab(state, value) {
            state.contextInfoBarOpenTab = value
        }
    },
    getters: {
        configs(state) {
            return state.configs;
        },
        contextInfoBarOpenTab(state) {
            return state.contextInfoBarOpenTab;
        }
    }
}
