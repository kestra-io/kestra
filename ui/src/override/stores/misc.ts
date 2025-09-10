import {defineStore} from "pinia";
import {apiUrl, apiUrlWithoutTenants} from "override/utils/route";
import {useApiStore} from "../../stores/api";
import * as BasicAuth from "../../utils/basicAuth"
import {ref} from "vue";
import {useAxios} from "../../utils/axios";



export const useMiscStore = defineStore("misc", () => {

    const configs = ref<Record<string, any>>()
    const contextInfoBarOpenTab = ref("")
    const theme = ref<"light" | "dark">("light")

    const axios = useAxios();


    async function loadConfigs() {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs`);
        configs.value = response.data;
        return response.data;
    }

    async function loadBasicAuthValidationErrors() {
        const response = await axios.get(`${apiUrlWithoutTenants()}/basicAuthValidationErrors`);
        return response.data;
    }

    async function loadAllUsages() {
        if (configs.value?.isBasicAuthInitialized && BasicAuth.isLoggedIn()) {
            const response = await axios.get(`${apiUrl()}/usages/all`);
            return response.data;
        }
        return [];
    }

    async function addBasicAuth(options: {
        firstName: string;
        lastName: string;
        username: string;
        password: string;
    }) {
        const email = options.username;

        localStorage.setItem("firstName", options.firstName);
        localStorage.setItem("lastName", options.lastName);

        await axios.post(`${apiUrl()}/basicAuth`, {
            uid: localStorage.getItem("uid"),
            username: email,
            password: options.password,
        });

        const apiStore = useApiStore();

        return apiStore.posthogEvents({
            type: "ossauth",
            iid: configs.value?.uuid,
            uid: localStorage.getItem("uid"),
            date: new Date().toISOString(),
            counter: 0,
            email: email
        });
    }

    return {
        configs,
        contextInfoBarOpenTab,
        theme,
        loadConfigs,
        loadBasicAuthValidationErrors,
        loadAllUsages,
        addBasicAuth
    }
});
