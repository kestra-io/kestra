import {ref} from "vue"
import {defineStore} from "pinia"
import type {MiscControllerConfiguration} from "@kestra-io/kestra-sdk"
import * as MiscAPI from "@kestra-io/kestra-sdk/misc"
import * as BasicAuth from "../../utils/basicAuth"
import {initPosthogIfEnabled} from "../../utils/posthog"
import {ensureUid} from "../../utils/uid"
import type {SelectedTheme} from "../../utils/utils"
import {useApiStore} from "../../stores/api"

export const useMiscStore = defineStore("misc", () => {
    const configs = ref<MiscControllerConfiguration>()
    const contextInfoBarOpenTab = ref("")
    const lastContextTab = ref("ai")
    const theme = ref<SelectedTheme>("syncWithSystem")
    /** Seeded by entry points such as "Fix with AI", then consumed and cleared by CopilotChat. */
    const copilotPrompt = ref<string | null>(null)
    /** Only read when `copilotNewThread` is set. */
    const copilotThreadTitle = ref<string | null>(null)
    /** Never set in OSS: without the EE thread list, a fresh thread would discard the previous conversation. */
    const copilotNewThread = ref(false)

    function openCopilot() {
        lastContextTab.value = "ai"
        contextInfoBarOpenTab.value = "ai"
    }

    function promptCopilot(prompt: string, options?: {title?: string, newThread?: boolean}) {
        copilotPrompt.value = prompt
        copilotThreadTitle.value = options?.title ?? null
        openCopilot()
    }

    /** Flushes, best effort, the analytics events queued before the configs were known. */
    async function loadConfigs() {
        const data = await MiscAPI.configuration()
        configs.value = data
        void useApiStore().flushQueuedEvents()
        return data
    }

    /** Public endpoint exposing only what the login and setup pages need. */
    function loadLoginConfig() {
        return MiscAPI.loginConfiguration()
    }

    function loadBasicAuthValidationErrors() {
        return MiscAPI.basicAuthConfigErrors()
    }

    async function loadAllUsages() {
        if (configs.value?.isBasicAuthInitialized && BasicAuth.isLoggedIn()) {
            return MiscAPI.usages()
        }
        return []
    }

    /** Creating the account logs the caller in, so the full configuration loads right after to drive analytics. */
    async function addBasicAuth(options: {
        username: string;
        password: string;
    }) {
        const email = options.username
        const uid = ensureUid()

        await MiscAPI.createBasicAuth({
            uid,
            username: email,
            password: options.password,
        })

        const freshConfigs = await loadConfigs()

        if (freshConfigs?.isUiAnonymousUsageEnabled === true) {
            void initPosthogIfEnabled(freshConfigs)
        }

        return useApiStore().posthogEvents({
            type: "ossauth",
            iid: freshConfigs?.uuid,
            uid,
            date: new Date().toISOString(),
            counter: 0,
            email: email,
        })
    }

    async function changeBasicAuth(options: {
        username: string;
        password: string;
        currentPassword: string;
    }) {
        await MiscAPI.createBasicAuth({
            uid: ensureUid(),
            username: options.username,
            password: options.password,
            currentPassword: options.currentPassword,
        }, {showMessageOnError: false} as Parameters<typeof MiscAPI.createBasicAuth>[1])
    }

    return {
        configs,
        contextInfoBarOpenTab,
        lastContextTab,
        theme,
        copilotPrompt,
        copilotThreadTitle,
        copilotNewThread,
        openCopilot,
        promptCopilot,
        loadConfigs,
        loadLoginConfig,
        loadBasicAuthValidationErrors,
        loadAllUsages,
        addBasicAuth,
        changeBasicAuth,
    }
})
