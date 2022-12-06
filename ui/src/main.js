
import {createApp} from "vue"
import {createI18n} from "vue-i18n"
import {createRouter, createWebHistory} from "vue-router"
import VueSidebarMenu from "vue-sidebar-menu"
import {createStore} from "vuex"
import VueGtag from "vue-gtag";
import {extendMoment} from "moment-range";
import ElementPlus from "element-plus"
import VueAxios from "vue-axios";
import moment from "moment"
import "moment/locale/fr"

import App from "./App.vue"
import createUnsavedChanged from "./utils/unsavedChange"

// import VueHotkey from "v-hotkey"

import "./utils/global"
import filters from "./utils/filters"
import routes from "./routes/routes"
import stores from "./stores/store"
import translations from "./translations.json"
import configureAxios from "./utils/http"
import Toast from "./utils/toast";

// charts
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

const app = createApp(App)

// store
let store = createStore(stores);
app.use(store);

// router
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

// l18n
let locale = localStorage.getItem("lang") || "en";

let i18n = createI18n({
    locale: locale,
    messages: translations,
    allowComposition: true,
    legacy: false,
    warnHtmlMessage: false,
});

app.use(i18n);


// moment
moment.locale(locale);
app.config.globalProperties.$moment = extendMoment(moment);

// others plugins
app.use(VueSidebarMenu);
app.use(Toast)

// filters
app.config.globalProperties.$filters = filters;

// element-plus
app.use(ElementPlus)

// navigation guard
createUnsavedChanged(app, store, router);

// axios
configureAxios((instance) => {
    app.use(VueAxios, instance);

    store.$http = app.$http;
    store.axios = app.axios;
}, store, router);


app.mount("#app")