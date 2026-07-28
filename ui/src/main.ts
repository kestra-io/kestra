import {createApp} from "vue"
import type {Router} from "vue-router"

import "./utils/monacoEnvironment"
import {setupPreloadErrorReloadHandler} from "./utils/preloadErrorReload"

setupPreloadErrorReloadHandler()

import App from "./App.vue"
import initApp from "./utils/init"
import {setupKestraHttp} from "./utils/kestraHttp"
import {useClient} from "@kestra-io/kestra-sdk"
import routes from "./routes/routes"
import en from "./translations/en.json"
import {setupTenantRouter} from "./composables/useTenant"
import * as BasicAuth from "./utils/basicAuth"
import {getCsrfToken} from "./utils/csrf"
import {useCoreStore} from "./stores/core"
import {useLayoutStore} from "./stores/layout"
import {useUnsavedChangesStore} from "./stores/unsavedChanges"
import {useMiscStore} from "override/stores/misc"
import {TASK_ICON_INJECTION_KEY} from "@kestra-io/design-system"
import TaskIcon from "./components/plugins/TaskIcon.vue"
import {registerServiceWorker} from "./utils/serviceWorker"
import {initPwaInstallCapture} from "./utils/pwaInstallState"

void registerServiceWorker()
initPwaInstallCapture()

const app = createApp(App)

// lets KsEditor and the topology package render real plugin icons without
// the design system depending on the app's plugin-icon API
app.provide(TASK_ICON_INJECTION_KEY, TaskIcon)

// Fail closed: an error probing the pre-auth endpoints is no evidence that setup is needed.
const handleAuthError = (to: {fullPath: string}) => {
    BasicAuth.logout()
    const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
    return {name: "login", query: fromPath ? {from: fromPath} : {}}
}

let httpClient: ReturnType<typeof setupKestraHttp> | undefined

function setupAxios(router: Router) {
    const coreStore = useCoreStore()
    const unsavedChangesStore = useUnsavedChangesStore()
    const layoutStore = useLayoutStore()

    function beforeLogout() {
        document.body.classList.add("login")
        unsavedChangesStore.unsavedChange = false
        layoutStore.setTopNavbar(undefined)
        BasicAuth.logout()
    }


    httpClient = setupKestraHttp({}, {
        coreStore,
        router,
        beforeLogout,
        isLoggedIn: () => !!BasicAuth.isLoggedIn(),
    })

    // Add CSRF token to every request - covers both generated-endpoint calls and
    // useClient() ad-hoc calls, since they share client.interceptors under the hood.
    httpClient.interceptors.request.use((request) => {
        const csrfToken = getCsrfToken()
        if (!csrfToken) return request
        const headers = new Headers(request.headers)
        headers.set("X-CSRF-TOKEN", csrfToken)
        return new Request(request, {headers})
    })

    return useClient()
}

// FIXME: any - guard args are untyped in the GuardFn interface
async function beforeResolve(router: Router, to: any, from: any): Promise<unknown> { // FIXME: any
    if(to.path === from.path && to.query === from.query) {
        return // Prevent navigation if the path and query are the same
    }

    try {
        const miscStore = useMiscStore()
        if(!httpClient) {
            setupAxios(router)
        }
        const loginConfig = await miscStore.loadLoginConfig()

        if(!loginConfig.isBasicAuthInitialized) {
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

        // Now that the user is authenticated, load the full instance configuration.
        await miscStore.loadConfigs()
    } catch (error) {
        console.error("Error during authentication check:", error)
        return handleAuthError(to)
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
