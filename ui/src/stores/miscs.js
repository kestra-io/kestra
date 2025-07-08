import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";
import {useApiStore} from "./api";

export default {
    namespaced: true,
    state: {
        configs: undefined,
        contextInfoBarOpenTab: "",
        theme: "light"
    },
    actions: {
        loadConfigs({commit}) {
            return this.$http.get(`${apiUrlWithoutTenants(this)}/configs`).then(response => {
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
            
            localStorage.setItem("firstName", options.firstName);
            localStorage.setItem("lastName", options.lastName);

            await this.$http.post(`${apiUrl(this)}/basicAuth`, {
                uid: localStorage.getItem("uid"),
                username: email,
                password: options.password,
            });

            const apiStore = useApiStore();

            return apiStore.posthogEvents({
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
        setTheme(state, theme) {
            state.theme = theme
        },
        setConfigs(state, configs) {
            state.configs = configs
        },
        setContextInfoBarOpenTab(state, value) {
            state.contextInfoBarOpenTab = value
        }
    },
    getters: {
        theme(state) {
            return state.theme;
        },
        configs(state) {
            return state.configs;
        },
        contextInfoBarOpenTab(state) {
            return state.contextInfoBarOpenTab;
        }
    }
}
