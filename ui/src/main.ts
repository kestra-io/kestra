import {createApp} from "vue"
import type {Router} from "vue-router"

import "./utils/monacoEnvironment"

const NodeTypesRaw = import.meta.glob("/node_modules/@types/node/**/*.d.ts", {eager: true, query: "?raw", import: "default"}) as Record<string, string>
function loadNodeTypes(tries = 0) {
    import("monaco-editor/esm/vs/editor/editor.api").then(({languages}) => {
        if (languages.typescript) {
            for (const path in NodeTypesRaw) {
                languages.typescript.typescriptDefaults.addExtraLib(NodeTypesRaw[path], `file://${path}`)
            }
        } else if (tries <= 15) {
            setTimeout(() => loadNodeTypes(tries + 1), (tries + 1) * 100)
        }
    })
}
loadNodeTypes()

import App from "./App.vue"
import initApp from "./utils/init"
import {configureAxios} from "@kestra-io/kestra-sdk"
import routes from "./routes/routes"
import en from "./translations/en.json"
import {setupTenantRouter} from "./composables/useTenant"
import * as BasicAuth from "./utils/basicAuth"
import {getCsrfToken} from "./utils/csrf"
import {useCoreStore} from "./stores/core"
import {useLayoutStore} from "./stores/layout"
import {useUnsavedChangesStore} from "./stores/unsavedChanges"
import {useAuthStore} from "override/stores/auth"
import {useMiscStore} from "override/stores/misc"


const app = createApp(App)

const handleAuthError = (error: Error, to: {fullPath: string}) => {
    if (error.message?.includes("401")) {
        BasicAuth.logout()
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    }
    return {name: "setup"}
}

let axiosInstance: ReturnType<typeof configureAxios> | undefined

function setupAxios(router: Router) {
    const coreStore = useCoreStore()
    const authStore = useAuthStore()
    const unsavedChangesStore = useUnsavedChangesStore()
    const layoutStore = useLayoutStore()

    function beforeLogout() {
        document.body.classList.add("login")
        unsavedChangesStore.unsavedChange = false
        layoutStore.setTopNavbar(undefined)
        BasicAuth.logout()
    }


    // axios
    axiosInstance = configureAxios({}, {
        authStore,
        coreStore,
        oss: true,
        router,
        beforeLogout,
        isLoggedIn: () => !!BasicAuth.isLoggedIn(),
        onAuthTimeout: beforeLogout,
        isImpersonating: () => !!window.sessionStorage.getItem("impersonate"),
    })

    axiosInstance.interceptors.request.use((config) => {
        const csrfToken = getCsrfToken()
        if (csrfToken) {
            config.headers = config.headers || {}
            config.headers["X-CSRF-TOKEN"] = csrfToken
        }
        return config
    })

    return axiosInstance
}

// FIXME: any - guard args are untyped in the GuardFn interface
async function beforeResolve(router: Router, to: any, from: any): Promise<unknown> { // FIXME: any
    if(to.path === from.path && to.query === from.query) {
        return // Prevent navigation if the path and query are the same
    }

    try {
        const miscStore = useMiscStore()
        if(!axiosInstance) {
            setupAxios(router)
        }
        const configs = await miscStore.loadConfigs()

        if(!configs.isBasicAuthInitialized) {
            // Since, Configs takes preference
            // we need to check if any regex validation error in BE.
            const validationErrors = await miscStore.loadBasicAuthValidationErrors()

            if (validationErrors?.length > 0) {
                // Creds exist in config but failed validation
                // Route to login to show errors
                if (to.name === "login") {
                    return
                }

                return {name: "login"}
            } else {
                // No creds in config - redirect to set it up
                if (to.name === "setup") {
                    return
                }

                return {name: "setup"}
            }
        }

        if ((to as {meta?: {anonymous?: boolean}}).meta?.anonymous === true) {
            if (to.name === "setup") {
                return {name: "login"}
            }
            return
        }

        const hasCredentials = BasicAuth.isLoggedIn()

        if (!hasCredentials) {
            const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
            return {name: "login", query: fromPath ? {from: fromPath} : {}}
        }

        // Check if basic auth setup is still in progress
        const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress")
        if (isSetupInProgress === "true") {
            return {name: "setup"}
        }
    } catch (error) {
        console.error("Error during authentication check:", error)
        return handleAuthError(error as Error, to)
    }
}

initApp(app, routes, null, en as Record<string, unknown>, {}, {beforeResolve: beforeResolve as (...args: unknown[]) => unknown}).then(({router, piniaStore}) => {


    // Setup tenant router
    setupTenantRouter(router, app)

    setupAxios(router)

    const $http = setupAxios(router)

    piniaStore.use(({store: piniaStoreLocal}) => {
        // FIXME: any
        ;(piniaStoreLocal as any).$http = $http
    })

    // mount
    router.isReady().then(() => app.mount("#app"))
})
