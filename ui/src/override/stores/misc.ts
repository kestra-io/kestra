import {defineStore} from "pinia"
import {apiUrl, apiUrlWithoutTenants} from "override/utils/route"
import {useApiStore} from "../../stores/api"
import * as BasicAuth from "../../utils/basicAuth"
import {ref} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import {initPosthogIfEnabled} from "../../utils/posthog"
import {ensureUid} from "../../utils/uid"
import type {SelectedTheme} from "../../utils/utils"



export const useMiscStore = defineStore("misc", () => {

    const configs = ref<Record<string, any>>()
    const contextInfoBarOpenTab = ref("")
    const lastContextTab = ref("news")
    const theme = ref<SelectedTheme>("syncWithSystem")

    const axios = useClient()


    async function loadConfigs() {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs`)
        configs.value = response.data
        // Best-effort: flush any queued analytics events once configs are known.
        void useApiStore().flushQueuedEvents()
        return response.data
    }

    // Public, unauthenticated endpoint exposing only what the login/setup UI needs.
    async function loadLoginConfig() {
        const response = await axios.get(`${apiUrlWithoutTenants()}/configs/login`)
        return response.data
    }

    async function loadBasicAuthValidationErrors() {
        const response = await axios.get(`${apiUrlWithoutTenants()}/basicAuthValidationErrors`)
        return response.data
    }

    async function loadAllUsages() {
        if (configs.value?.isBasicAuthInitialized && BasicAuth.isLoggedIn()) {
            const response = await axios.get(`${apiUrl()}/usages/all`)
            return response.data
        }
        return []
    }

    async function addBasicAuth(options: {
        username: string;
        password: string;
    }) {
        const email = options.username
        const uid = ensureUid()

        await axios.post(`${apiUrl()}/basicAuth`, {
            uid,
            username: email,
            password: options.password,
        })

        // The call above logs the caller in (it sets the auth cookie on success), so the
        // full configuration can now be loaded to drive analytics for this event.
        const freshConfigs = await loadConfigs()

        if (freshConfigs?.isUiAnonymousUsageEnabled === true) {
            void initPosthogIfEnabled(freshConfigs)
        }

        const apiStore = useApiStore()

        return apiStore.posthogEvents({
            type: "ossauth",
            iid: freshConfigs?.uuid,
            uid,
            date: new Date().toISOString(),
            counter: 0,
            email: email,
        })
    }

    return {
        configs,
        contextInfoBarOpenTab,
        lastContextTab,
        theme,
        loadConfigs,
        loadLoginConfig,
        loadBasicAuthValidationErrors,
        loadAllUsages,
        addBasicAuth,
    }
})
