import filters from "./filters"
import "moment/locale/fr"
import "./utils/global"

import App from "./App.vue"
import BootstrapVue from "bootstrap-vue"
import Vue from "vue"
import {createI18n} from "vue-i18n"
import NProgress from "vue-nprogress"

import {createRouter, createWebHistory} from "vue-router"
import VueSidebarMenu from "vue-sidebar-menu"
import {createStore} from "vuex"
import VueGtag from "vue-gtag";

import configureHttp from "./http"
import Toast from "./utils/toast";
import {extendMoment} from "moment-range";
import translations from "./translations.json"
import moment from "moment"
import routes from "./routes/routes"
import stores from "./stores/store"
import vSelect from "vue-select"
import VueHotkey from "v-hotkey"

import {
    Chart,
    CategoryScale,
    LinearScale,
    BarElement,
    BarController,
    LineElement,
    LineController,
    PointElement,
    Tooltip,
    Filler
} from "chart.js";
import VueAxios from "vue-axios";

Chart.register(
    CategoryScale,
    LinearScale,
    BarElement,
    BarController,
    LineElement,
    LineController,
    PointElement,
    Tooltip,
    Filler
);

const app = Vue.createApp(App)

// store
let store = createStore(stores);
app.use(store);

// router
/* eslint-disable */
let router = createRouter({
    history: createWebHistory(KESTRA_UI_PATH),
    routes
});
app.use(router)

// Google Analytics
if (KESTRA_GOOGLE_ANALYTICS !== null) {
    app.use(
        VueGtag,
        {
            config: {id: KESTRA_GOOGLE_ANALYTICS}
        },
        router
    );
}
/* eslint-enable */

// l18n
let locale = localStorage.getItem("lang") || "en";

let i18n = createI18n({
    locale: locale,
    messages: translations
});


app.use(i18n);

// moment
moment.locale(locale);
app.config.globalProperties.$moment = extendMoment(moment);

// nprogress
const nprogress = new NProgress()
app.use()
// Vue.use(NProgress, {
//     latencyThreshold: 50,
// })

// others plugins
app.use(VueHotkey)
app.use(VueSidebarMenu);
app.use(BootstrapVue);

app.use(Toast)

app.component("VSelect", vSelect);

// filters
app.config.productionTip = false;
app.config.globalProperties.$filters = filters;

// axios
configureHttp((instance) => {
    app.use(VueAxios, instance);

    store.$http = app.$http;
    store.axios = app.axios;

}, store, nprogress);


app.mount("#app")