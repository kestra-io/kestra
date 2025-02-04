import {createStore} from "vuex";
import {createRouter, createWebHistory} from "vue-router";
import VueGtag from "vue-gtag";
import {setI18nLanguage, loadLocaleMessages, setupI18n} from "../translations/i18n";
import moment from "moment-timezone";
import "moment/dist/locale/de"
import "moment/dist/locale/es"
import "moment/dist/locale/fr"
import "moment/dist/locale/hi"
import "moment/dist/locale/it"
import "moment/dist/locale/ja"
import "moment/dist/locale/ko"
import "moment/dist/locale/pl"
import "moment/dist/locale/pt"
import "moment/dist/locale/ru"
import "moment/dist/locale/zh-cn"
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
    Legend,
    ArcElement,
    DoughnutController,
} from "chart.js";
import {TreemapController, TreemapElement} from "chartjs-chart-treemap"
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
import TaskExpression from "../components/flows/tasks/TaskExpression.vue";
import TaskEnum from "../components/flows/tasks/TaskEnum.vue";
import TaskNumber from "../components/flows/tasks/TaskNumber.vue";
import TaskObject from "../components/flows/tasks/TaskObject.vue";
import TaskString from "../components/flows/tasks/TaskString.vue";
import TaskTask from "../components/flows/tasks/TaskTask.vue";
import TaskOneOf from "../components/flows/tasks/TaskOneOf.vue";
import TaskSubflowNamespace from "../components/flows/tasks/TaskSubflowNamespace.vue";
import TaskSubflowId from "../components/flows/tasks/TaskSubflowId.vue";
import TaskSubflowInputs from "../components/flows/tasks/TaskSubflowInputs.vue";
import LeftMenuLink from "../components/LeftMenuLink.vue";
import RouterMd from "../components/utils/RouterMd.vue";
import Utils from "./utils";
import TaskTaskRunner from "../components/flows/tasks/TaskTaskRunner.vue";

export default async (app, routes, stores, translations, additionalTranslations = {}) => {
    // charts
    Chart.register(
        CategoryScale,
        LinearScale,
        BarElement,
        BarController,
        LineElement,
        LineController,
        PointElement,
        Filler,
        ArcElement,
        DoughnutController,
        Tooltip,
        Legend,
        CategoryScale,
        LinearScale,
        TreemapController,
        TreemapElement
    );

    // store
    let store = createStore(stores);
    app.use(store);


    // router
    let router = createRouter({
        history: createWebHistory(window.KESTRA_UI_PATH),
        routes
    });

    /**
     * Manage docId initialization for Contextual docs
     */
    router.beforeEach((to, from, next) => {
        // set the docId from the path
        // so it has a default
        const pathArray = to.path.split("/");
        const docId = pathArray[pathArray.length-1];
        store.commit("doc/setDocId", docId);

        // propagate showDocId query param
        // to the next page to facilitate docs binding
        if(to.query["showDocId"] === undefined && from.query["showDocId"] !== undefined){
            next({path: to.path, query: {...to.query, showDocId: from.query["showDocId"]}})
        }else{
            next()
        }
    })

    router.afterEach((to) => {
        window.dispatchEvent(new CustomEvent("KestraRouterAfterEach", to))
    })

    // avoid loading router in storybook
    // as it conflicts with storybook's
    if(routes.length){
        app.use(router)
    }

    // Google Analytics
    if (window.KESTRA_GOOGLE_ANALYTICS !== null) {
        app.use(
            VueGtag,
            {
                config: {id: window.KESTRA_GOOGLE_ANALYTICS}
            },
            router
        );
    }



    // l18n
    let locale = Utils.getLang();

    let i18n = setupI18n({
        locale: "en",
        messages: translations,
        allowComposition: true,
        legacy: false,
        warnHtmlMessage: false,
    });

    if(locale !== "en"){
        await loadLocaleMessages(i18n, locale, additionalTranslations);
        await setI18nLanguage(i18n, locale);
    }
    
    app.use(i18n);

    // moment
    moment.locale(locale);
    app.config.globalProperties.$moment = extendMoment(moment);

    // others plugins
    app.use(VueSidebarMenu);
    app.use(Toast)
    app.provide("Toast", Toast)
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
    app.component("TaskExpression", TaskExpression)
    app.component("TaskEnum", TaskEnum)
    app.component("TaskNumber", TaskNumber)
    app.component("TaskObject", TaskObject)
    app.component("TaskComplex", TaskComplex)
    app.component("TaskString", TaskString)
    app.component("TaskTask", TaskTask)
    app.component("TaskOneOf", TaskOneOf)
    app.component("TaskSubflowNamespace", TaskSubflowNamespace)
    app.component("TaskSubflowId", TaskSubflowId)
    app.component("TaskSubflowInputs", TaskSubflowInputs)
    app.component("TaskTaskRunner", TaskTaskRunner)
    app.component("LeftMenuLink", LeftMenuLink)
    app.component("RouterMd", RouterMd);
    const components = {
        ...(import.meta.glob("../../node_modules/@nuxtjs/mdc/dist/runtime/components/prose/*.vue", {eager: true})),
        ...(import.meta.glob("../../node_modules/@kestra-io/ui-libs/src/components/content/*.vue", {eager: true})),
        ...(import.meta.glob("../components/content/*.vue", {eager: true})),
    };
    const componentsByName = Object.entries(components)
        .map(([path, component]) => [path.replace(/^.*\/(.*)\.vue$/, "$1"), component.default]);
    const componentsNames = componentsByName.map(([name]) => name);
    componentsByName.filter(([name], index) => componentsNames.lastIndexOf(name) === index)
        .forEach(([name, component]) => app.component(name, component));

    app.config.globalProperties.append = (path, pathToAppend) => path + (path.endsWith("/") ? "" : "/") + pathToAppend

    return {store, router};
}
