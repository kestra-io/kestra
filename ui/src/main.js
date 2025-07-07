import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import en from "./translations/en.json";
import stores from "./stores/store";
import {setupTenantRouter} from "./composables/useTenant";


const app = createApp(App)

initApp(app, routes, stores, en).then(({store, router, piniaStore}) => {
    
    router.beforeEach(async (to, from, next) => {
        if (["login", "setup"].includes(to.name)) {
            return next();
        }
        
        const hasCredentials = localStorage.getItem("basicAuthCredentials") !== null;
        const isSetupInProgress = localStorage.getItem("basicAuthSetupInProgress") === "true";
        
        try {
            if (!store.getters["misc/configs"]) {
                await store.dispatch("misc/loadConfigs");
            }
            
            const configs = store.getters["misc/configs"];
            const hasCompletedSetup = localStorage.getItem("basicAuthSetupCompleted") === "true";
            
            if (configs) {
                if (configs.isBasicAuthEnabled) {
                    if (!hasCredentials) {
                        return next({name: "login", query: {from: to.fullPath}});
                    }
                    if (hasCompletedSetup) {
                        localStorage.removeItem("basicAuthSetupCompleted");
                    }
                    if (isSetupInProgress) {
                        localStorage.removeItem("basicAuthSetupInProgress");
                    }
                    return next();
                }
                
                if (!configs.isBasicAuthEnabled && !hasCompletedSetup) {
                    return next({name: "setup"});
                }
            }
            
            if (!hasCredentials && !isSetupInProgress) {
                return next({name: "login", query: {from: to.fullPath}});
            }
            
            return next();
            
        } catch (error) {
            console.error("Router guard error:", error);
            localStorage.removeItem("basicAuthCredentials");
            return next({name: "login"});
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

