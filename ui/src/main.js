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
import VueMoment from 'vue-moment'
import VueProgressBar from 'vue-progressbar';
import VueSSE from 'vue-sse';
import VueSidebarMenu from 'vue-sidebar-menu'
import configureHttp from './http'
import { extendMoment } from 'moment-range';
import i18n from './i18n'
import moment from 'moment'
import router from './routes/router'
import store from './stores/store'
import vSelect from 'vue-select'

Vue.use(VueProgressBar, {
  color: 'rgb(143, 255, 199)',
  failedColor: 'red',
  height: '2px'
})
Vue.use(VueSSE);
Vue.use(VueMoment, { moment: extendMoment(moment) });
Vue.use(VueSidebarMenu);
Vue.use(BootstrapVue);

Vue.component('v-select', vSelect);
Vue.component('v-s', VerticalSeparator);

Vue.config.productionTip = false;

configureHttp(() => {
  new Vue({
    render: h => h(App),
    router,
    store,
    i18n
  }).$mount('#app')
}, store);