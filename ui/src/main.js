import {createApp} from "vue"
import VueAxios from "vue-axios";
import axios from "axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import en from "./translations/en.json";
import stores from "./stores/store";
import {setupTenantRouter} from "./composables/useTenant";
import {apiUrlWithoutTenants} from "./override/utils/route";


const app = createApp(App)

const validateCredentials = async (credentials) => {
    const [username, password] = atob(credentials).split(":")
    
    const response = await axios.post(`${apiUrlWithoutTenants()}/basicAuth`, {
        username, 
        password
    })
    
    return response.data
}

const handleAuthError = (error, to) => {
    if (error.message?.includes("401") || error.message?.includes("HTTP 401")) {
        localStorage.removeItem("basicAuthCredentials")
        const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
        return {name: "login", query: fromPath ? {from: fromPath} : {}}
    }
    return {name: "setup"}
}

initApp(app, routes, stores, en).then(({store, router, piniaStore}) => {
    router.beforeEach(async (to, from, next) => {
        if (["login", "setup"].includes(to.name)) {
            return next();
        }
        
        const hasCredentials = localStorage.getItem("basicAuthCredentials") !== null
        
        if (!hasCredentials) {
            const fromPath = to.fullPath !== "/ui/login" ? to.fullPath : undefined
            return next({name: "login", query: fromPath ? {from: fromPath} : {}})
        }
        
        try {
            const basicAuthCredentials = localStorage.getItem("basicAuthCredentials")
            if (basicAuthCredentials) {
                await validateCredentials(basicAuthCredentials)
            }
            
            await store.dispatch("misc/loadConfigs")
            const configs = store.getters["misc/configs"]
            
            if (!configs?.isBasicAuthInitialized) {
                return next({name: "setup"})
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
    }, store, router);

    piniaStore.vuexStore = store;
    app.config.globalProperties.$isOss = true; // Set to true for OSS version

    // mount
    app.mount("#app")
});

