import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import en from "./translations/en.json";
import stores from "./stores/store";
import {setupTenantRouter} from "./composables/useTenant";
import * as BasicAuth from "./utils/basicAuth";
import {useMiscStore} from "./stores/misc";


const app = createApp(App)

const handleAuthError = (error, to) => {
    if (error.message?.includes("401")) {
        BasicAuth.logout()
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    }
    return {name: "setup"}
}

initApp(app, routes, stores, en).then(({store, router, piniaStore}) => {
    router.beforeEach(async (to, _from, next) => {
        if (["login", "setup"].includes(to.name)) {
            return next();
        }

        try {
            const miscStore = useMiscStore();
            const configs = await miscStore.loadConfigs();

            if(!configs.isBasicAuthInitialized) {
                // Since, Configs takes preference 
                // we need to check if any regex validation error in BE.
                const validationErrors = await miscStore.loadBasicAuthValidationErrors()
                
                if (validationErrors?.length > 0) {
                    // Creds exist in config but failed validation
                    // Route to login to show errors
                    return next({name: "login"})
                } else {
                    // No creds in config - redirect to set it up
                    return next({name: "setup"})
                }
            }

            const hasCredentials = BasicAuth.isLoggedIn()

            if (!hasCredentials) {
                const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
                return next({name: "login", query: fromPath ? {from: fromPath} : {}})
            }

            // Check if basic auth setup is still in progress
            const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress")
            if (isSetupInProgress === "true") {
                return next({name: "setup"})
            }

            return next();
        } catch (error) {
            return next(handleAuthError(error, to))
        }
    });

    // Setup tenant router
    setupTenantRouter(router, app);

    // axios
    configureAxios((instance) => {
        app.use(VueAxios, instance);
        app.provide("axios", instance);
        store.$http = app.$http;
        store.axios = app.axios;
        piniaStore.$http = app.$http;
    }, store, router, true);

    piniaStore.vuexStore = store;
    app.config.globalProperties.$isOss = true; // Set to true for OSS version

    // mount
    app.mount("#app")
});

