import {createApp} from "vue"
import VueAxios from "vue-axios";

import App from "./App.vue"
import initApp from "./utils/init"
import configureAxios from "./utils/axios"
import routes from "./routes/routes";
import de from "./translations/de.json";
import en from "./translations/en.json";
import es from "./translations/es.json";
import fr from "./translations/fr.json";
import hi from "./translations/hi.json";
import it from "./translations/it.json";
import ja from "./translations/ja.json";
import ko from "./translations/ko.json";
import pl from "./translations/pl.json";
import pt from "./translations/pt.json";
import ru from "./translations/ru.json";
import zh_CN from "./translations/zh_CN.json"
import stores from "./stores/store";

const app = createApp(App)
const translations = {...de,...en,...es,...fr,...hi,...it,...ja,...ko,...pl,...pt,...ru,...zh_CN}

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