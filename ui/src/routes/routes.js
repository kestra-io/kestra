import Settings from '../components/settings/Settings.vue'
import Flows from '../components/flows/Flows.vue'
import FlowRoot from '../components/flows/FlowRoot.vue'
import FlowEdit from '../components/flows/FlowEdit.vue'
import Executions from '../components/executions/Executions.vue'
import ExecutionRoot from '../components/executions/ExecutionRoot.vue'
import Plugin from '../components/plugins/Plugin.vue'

export default {
    mode: 'history',
    base: '/ui/',
    routes: [
        //Flows
        { name: 'home', path: '/', component: Flows },
        { name: 'flowsList', path: '/flows', component: Flows },
        { name: 'flowsAdd', path: '/flows/new', component: FlowEdit },
        { name: 'flowEdit', path: '/flows/edit/:namespace/:id', component: FlowRoot },

        //Executions
        { name: 'executionsList', path: '/executions', component: Executions },
        { name: 'executionEdit', path: '/executions/:namespace/:flowId/:id', component: ExecutionRoot },

        //Executions
        { name: 'plugin', path: '/plugins', component: Plugin },
        { name: 'pluginView', path: '/plugins/:cls', component: Plugin },

        //Settings
        { name: 'settings', path: '/settings', component: Settings },
    ]
}
