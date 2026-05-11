import {createApp} from "vue"

import App from "./App.vue"
import initApp from "./utils/init"
import {configureAxios} from "@kestra-io/kestra-sdk"
import routes from "./routes/routes"
import en from "./translations/en.json"
import {setupTenantRouter} from "./composables/useTenant"
import * as BasicAuth from "./utils/basicAuth"
import {useCoreStore} from "./stores/core"
import {useLayoutStore} from "./stores/layout"
import {useUnsavedChangesStore} from "./stores/unsavedChanges"
import {useAuthStore} from "override/stores/auth"
import {useMiscStore} from "override/stores/misc"


const app = createApp(App)

const handleAuthError = (error, to) => {
    if (error.message?.includes("401")) {
        BasicAuth.logout()
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    }
    return {name: "setup"}
}

initApp(app, routes, null, en).then(({router, piniaStore}) => {
    router.beforeEach(async (to, from) => {
        if(to.path === from.path && to.query === from.query) {
            return // Prevent navigation if the path and query are the same
        }

        try {
            const miscStore = useMiscStore()
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

            if (to.meta?.anonymous === true) {
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
            return handleAuthError(error, to)
        }
    })

    // Setup tenant router
    setupTenantRouter(router, app)

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
    const axiosInstance = configureAxios({}, {
        authStore,
        coreStore,
        oss: true,
        router,
        beforeLogout,
        onAuthTimeout: beforeLogout,
        isImpersonating: () => window.sessionStorage.getItem("impersonate"),
    }) 

    piniaStore.use(({store: piniaStoreLocal}) => {
        piniaStoreLocal.$http = axiosInstance
    })

    
    // mount
    router.isReady().then(() => app.mount("#app"))
})
