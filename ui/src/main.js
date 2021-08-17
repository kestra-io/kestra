import "./filters"
import "moment/locale/fr"
import "./utils/global"
import "./custom.scss"
// @TODO: move to scss
import "vue-material-design-icons/styles.css";

import App from "./App.vue"
import BootstrapVue from "bootstrap-vue"
import Vue from "vue"
import VueCompositionAPI from "@vue/composition-api"
import VueI18n from "vue-i18n"
import VueMoment from "vue-moment"
import NProgress from "vue-nprogress"

import VueRouter from "vue-router"
import VueSidebarMenu from "vue-sidebar-menu"
import Vuex from "vuex";
import VueAnalytics from "vue-analytics";

import configureHttp from "./http"
import Toast from "./utils/toast";
import {extendMoment} from "moment-range";
import Translations from "./translations.json"
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

let app = document.querySelector("#app");

if (app) {
  Vue.use(VueCompositionAPI)
  Vue.use(Vuex)
  let store = new Vuex.Store(stores);

  Vue.use(VueRouter);
  let router = new VueRouter(routes);

  /* eslint-disable */
  if (KESTRA_GOOGLE_ANALYTICS !== null) {
    Vue.use(VueAnalytics, {
      id: KESTRA_GOOGLE_ANALYTICS,
      router
    });
  }
  /* eslint-enable */

  Vue.use(VueI18n);

  let locale = localStorage.getItem("lang") || "en";

  let i18n = new VueI18n({
    locale: locale,
    messages: Translations
  });

  moment.locale(locale)

  const nprogress = new NProgress()
  Vue.use(NProgress, {
    latencyThreshold: 50,
  })

  Vue.use(VueHotkey)
  Vue.use(VueMoment, {moment: extendMoment(moment)});
  Vue.use(VueSidebarMenu);
  Vue.use(BootstrapVue);

  Vue.use(Toast)

  Vue.component("VSelect", vSelect);

  Vue.config.productionTip = false;

  configureHttp(() => {
    new Vue({
      render: h => h(App),
      router: router,
      store,
      i18n,
      nprogress
    }).$mount(app)
  }, store, nprogress);
}
