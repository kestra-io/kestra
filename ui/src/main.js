import Vue from 'vue'
import App from './App.vue'
import router from './routes/router'
import store from './stores/store'
import BootstrapVue from 'bootstrap-vue'
import configureHttp from './http'
import VueSidebarMenu from 'vue-sidebar-menu'
import i18n from './i18n'
import vSelect from 'vue-select'
import VueMoment from 'vue-moment'
import VueSSE from 'vue-sse';
import moment from 'moment'
import './filters'
import 'moment/locale/fr'
import './utils/global'
import './custom.scss'
import VerticalSeparator from './components/layout/VerticalSeparator'
// @TODO: move to scss
import 'vue-material-design-icons/styles.css';
import VueProgressBar from 'vue-progressbar';
import {extendMoment} from 'moment-range';

Vue.use(VueProgressBar, {
  color: 'rgb(143, 255, 199)',
  failedColor: 'red',
  height: '2px'
})
Vue.use(VueSSE);
Vue.use(VueMoment, { moment });
Vue.use(VueSidebarMenu);
Vue.use(BootstrapVue);
Vue.use(extendMoment(moment));
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