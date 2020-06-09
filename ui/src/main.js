import './filters'
import 'moment/locale/fr'
import './utils/global'
import './custom.scss'
// @TODO: move to scss
import 'vue-material-design-icons/styles.css';

import App from './App.vue'
import BootstrapVue from 'bootstrap-vue'
import VerticalSeparator from './components/layout/VerticalSeparator'
import Vue from 'vue'
import VueI18n from 'vue-i18n'
import VueMoment from 'vue-moment'
import VueProgressBar from 'vue-progressbar';
import VueRouter from 'vue-router'
import VueSSE from 'vue-sse';
import VueSidebarMenu from 'vue-sidebar-menu'
import Vuex from "vuex";

import configureHttp from './http'
import Toast from "./utils/toast";
import { extendMoment } from 'moment-range';
import Translations from './translations.json'
import moment from 'moment'
import routes from './routes/routes'
import stores from './stores/store'
import vSelect from 'vue-select'

let app = document.querySelector('#app');

if (app) {
  Vue.use(Vuex)
  let store = new Vuex.Store(stores);

  Vue.use(VueRouter);
  let router = new VueRouter(routes);

  Vue.use(VueI18n);

  let i18n = new VueI18n({
    locale: localStorage.getItem('lang') || 'en',
    messages: Translations
  });

  Vue.use(VueProgressBar, {
    color: 'rgb(143, 255, 199)',
    failedColor: 'red',
    height: '2px'
  })

  Vue.use(VueSSE);
  Vue.use(VueMoment, { moment: extendMoment(moment) });
  Vue.use(VueSidebarMenu);
  Vue.use(BootstrapVue);

  Vue.use(Toast)

  Vue.component('v-select', vSelect);
  Vue.component('v-s', VerticalSeparator);

  Vue.config.productionTip = false;

  configureHttp(() => {
    new Vue({
      render: h => h(App),
      router: router,
      store,
      i18n
    }).$mount(app)
  }, store);
}
