import {createStore} from "vuex";
import {createRouter, createWebHistory} from "vue-router";
import VueGtag from "vue-gtag";
import {createI18n} from "vue-i18n";
import moment from "moment/moment";
import "moment/locale/fr"
import {extendMoment} from "moment-range";
import VueSidebarMenu from "vue-sidebar-menu";
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
    Filler,
    ArcElement,
    DoughnutController,
} from "chart.js";
import {TreemapController, TreemapElement} from "chartjs-chart-treemap"
import {MatrixController, MatrixElement} from "chartjs-chart-matrix";


import Toast from "./toast";
import filters from "./filters";
import ElementPlus from "element-plus";
import createUnsavedChanged from "./unsavedChange";
import createEventsRouter from "./eventsRouter";
import "./global"

export default (app, routes, stores, translations) => {
    // charts
    Chart.register(
        CategoryScale,
        LinearScale,
        BarElement,
        BarController,
        LineElement,
        LineController,
        PointElement,
        Tooltip,
        Filler,
        ArcElement,
        DoughnutController,
        Tooltip,
        CategoryScale,
        LinearScale,
        TreemapController,
        TreemapElement,
        MatrixController,
        MatrixElement
    );

    // store
    let store = createStore(stores);
    app.use(store);

    /* eslint-disable no-undef */
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
    /* eslint-enable no-undef */


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
    createEventsRouter(app, store, router);

    return {store, router};
}
