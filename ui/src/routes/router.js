import Vue from 'vue'
import VueRouter from 'vue-router'
import Settings from '../components/settings/Settings.vue'
import Flows from '../components/flows/Flows.vue'
import FlowEdit from '../components/flows/FlowEdit.vue'
import Executions from '../components/executions/Executions.vue'
import ExecutionRoot from '../components/executions/ExecutionRoot.vue'
import ExecutionConfiguration from '../components/executions/ExecutionConfiguration.vue'

Vue.use(VueRouter)


export default new VueRouter({
    mode: 'history',
    routes: [
        //Flows
        { name: 'flows', path: '/flows', component: Flows },
        { name: 'flowsAdd', path: '/flows/add', component: FlowEdit },
        { name: 'flowsEdit', path: '/flows/edit/:namespace/:id', component: FlowEdit },
        //Executions
        { name: 'executions', path: '/executions/:namespace/:flowId', component: Executions },
        { name: 'executions-raw', path: '/executions', component: Executions },
        { name: 'execution', path: '/execution/:namespace/:flowId/:id', component: ExecutionRoot },
        { name: 'executionConfiguration', path: '/execution/configuration/:namespace/:id', component: ExecutionConfiguration },
        //Settings
        { name: 'settings', path: '/settings', component: Settings },
    ]
})