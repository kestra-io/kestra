import {createStore} from "vuex";
import {createRouter, createWebHistory} from "vue-router";
import VueGtag from "vue-gtag";
import {createI18n} from "vue-i18n";
import moment from "moment-timezone";
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
import Vue3Tour from "vue3-tour"
import VueVirtualScroller from "vue-virtual-scroller";

import Toast from "./toast";
import filters from "./filters";
import ElementPlus from "element-plus";
import createUnsavedChanged from "./unsavedChange";
import createEventsRouter from "./eventsRouter";
import "./global"

import TaskArray from "../components/flows/tasks/TaskArray.vue";
import TaskBoolean from "../components/flows/tasks/TaskBoolean.vue";
import TaskComplex from "../components/flows/tasks/TaskComplex.vue";
import TaskCondition from "../components/flows/tasks/TaskCondition.vue";
import TaskDict from "../components/flows/tasks/TaskDict.vue";
import TaskDynamic from "../components/flows/tasks/TaskDynamic.vue";
import TaskEnum from "../components/flows/tasks/TaskEnum.vue";
import TaskNumber from "../components/flows/tasks/TaskNumber.vue";
import TaskObject from "../components/flows/tasks/TaskObject.vue";
import TaskString from "../components/flows/tasks/TaskString.vue";
import TaskTask from "../components/flows/tasks/TaskTask.vue";

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
    app.use(Vue3Tour)
    app.use(VueVirtualScroller)

    // filters
    app.config.globalProperties.$filters = filters;

    // element-plus
    app.use(ElementPlus)

    // navigation guard
    createUnsavedChanged(app, store, router);
    createEventsRouter(app, store, router);

    // Task have some recursion and need to be register globally
    app.component("TaskArray", TaskArray)
    app.component("TaskBoolean", TaskBoolean)
    app.component("TaskCondition", TaskCondition)
    app.component("TaskDict", TaskDict)
    app.component("TaskDynamic", TaskDynamic)
    app.component("TaskEnum", TaskEnum)
    app.component("TaskNumber", TaskNumber)
    app.component("TaskObject", TaskObject)
    app.component("TaskComplex", TaskComplex)
    app.component("TaskString", TaskString)
    app.component("TaskTask", TaskTask)

    return {store, router};
}
