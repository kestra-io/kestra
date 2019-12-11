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
import moment from 'moment'
import 'moment/locale/fr'
import './utils'
import './custom.scss'
import 'vue-sidebar-menu/dist/vue-sidebar-menu.css'
import 'vue-material-design-icons/styles.css';

Vue.use(VueMoment, {
  moment
})
Vue.use(VueSidebarMenu)
Vue.use(BootstrapVue)
Vue.component('v-select', vSelect)

Vue.config.productionTip = false

configureHttp(() => {
  new Vue({
    render: h => h(App),
    router,
    store,
    i18n
  }).$mount('#app')
})