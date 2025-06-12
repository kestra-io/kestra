import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import en from "./translations/en.json";
import stores from "./stores/store";

const app = createApp(App)

initApp(app, routes, stores, en).then(({store, router}) => {
    // axios
    configureAxios((instance) => {
        app.use(VueAxios, instance);
        app.provide("axios", instance);

        store.$http = app.$http;
        store.axios = app.axios;
    }, store, router);

    app.config.globalProperties.$isOss = true; // Set to true for OSS version

    // mount
    app.mount("#app")
});

