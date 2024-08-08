import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import fr from "./translations/fr.json";
import en from "./translations/en.json";
import de from "./translations/de.json";
import pl from "./translations/pl.json";
import stores from "./stores/store";

const app = createApp(App)
const translations = {...fr,...en,...de,...pl}
const {store, router} = initApp(app, routes, stores, translations);

// Passing toast to VUEX store to be used in modules
store.$toast = app.config.globalProperties.$toast();

// axios
configureAxios((instance) => {
    app.use(VueAxios, instance);
    app.provide("axios", instance);

    store.$http = app.$http;
    store.axios = app.axios;
}, store, router);

// mount
app.mount("#app")