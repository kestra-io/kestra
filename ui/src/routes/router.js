import Vue from 'vue'
import VueRouter from 'vue-router'
import Settings from '../components/settings/Settings.vue'
import Flows from '../components/flows/Flows.vue'
import FlowsAgg from '../components/flows/FlowsAgg.vue'
import FlowRoot from '../components/flows/FlowRoot.vue'
import FlowEdit from '../components/flows/FlowEdit.vue'
import Executions from '../components/executions/Executions.vue'
import ExecutionRoot from '../components/executions/ExecutionRoot.vue'

Vue.use(VueRouter);


export default new VueRouter({
    mode: 'history',
    base: '/ui/',
    routes: [
        //Flows
        { name: 'home', path: '/', component: Flows },
        { name: 'flowsList', path: '/flows', component: FlowsAgg },
        { name: 'flowsAdd', path: '/flows/add', component: FlowEdit },
        { name: 'flow', path: '/flows/:namespace/:id', component: FlowRoot },
        //Executions
        { name: 'executionsByFlow', path: '/executions/:namespace/:flowId', component: Executions },
        { name: 'executionsList', path: '/executions', component: Executions },
        { name: 'execution', path: '/executions/:namespace/:flowId/:id', component: ExecutionRoot },

        //Settings
        { name: 'settings', path: '/settings', component: Settings },
    ]
})
