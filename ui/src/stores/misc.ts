import {defineStore} from "pinia";
import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";
import {useApiStore} from "./api";

interface MiscState {
    configs: any | undefined;
    contextInfoBarOpenTab: string;
    theme: string;
}

export const useMiscStore = defineStore("misc", {
    state: (): MiscState => ({
        configs: undefined,
        contextInfoBarOpenTab: "",
        theme: "light"
    }),

    getters: {
        getConfigs: (state) => state.configs,
        getContextInfoBarOpenTab: (state) => state.contextInfoBarOpenTab,
        getTheme: (state) => state.theme,
    },

    actions: {
        async loadConfigs() {
            const response = await this.$http.get(`${apiUrlWithoutTenants()}/configs`);
            this.configs = response.data;
            return response.data;
        },

        async loadAllUsages() {
            const response = await this.$http.get(`${apiUrl(this.vuexStore)}/usages/all`);
            return response.data;
        },

        async addBasicAuth(options: {
            firstName: string;
            lastName: string;
            username: string;
            password: string;
        }) {
            const email = options.username;
            
            localStorage.setItem("firstName", options.firstName);
            localStorage.setItem("lastName", options.lastName);

            await this.$http.post(`${apiUrl(this.vuexStore)}/basicAuth`, {
                uid: localStorage.getItem("uid"),
                username: email,
                password: options.password,
            });

            const apiStore = useApiStore();

            return apiStore.posthogEvents({
                type: "ossauth",
                iid: this.configs.uuid,
                uid: localStorage.getItem("uid"),
                date: new Date().toISOString(),
                counter: 0,
                email: email
            });
        },

        setTheme(theme: string) {
            this.theme = theme;
        },

        setConfigs(configs: any) {
            this.configs = configs;
        },

        setContextInfoBarOpenTab(value: string) {
            this.contextInfoBarOpenTab = value;
        }
    }
});
