import Vue from 'vue'
import VueRouter from 'vue-router'
import Flows from '../components/flows/Flows.vue'
import FlowEdit from '../components/flows/FlowEdit.vue'
import Settings from '../components/settings/Settings.vue'
Vue.use(VueRouter)


export default new VueRouter({
    mode: 'history',
    routes: [
        //Flows
        { name: 'flows', path: '/flows', component: Flows },
        { name: 'flowsAdd', path: '/flows/add', component: FlowEdit },
        { name: 'flowsEdit', path: '/flows/edit/:namespace/:id', component: FlowEdit },
        //Settings
        { name: 'settings', path: '/settings', component: Settings },
    ]
})