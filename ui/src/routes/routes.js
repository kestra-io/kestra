import ExecutionRoot from '../components/executions/ExecutionRoot.vue'
import Executions from '../components/executions/Executions.vue'
import FlowEdit from '../components/flows/FlowEdit.vue'
import FlowRoot from '../components/flows/FlowRoot.vue'
import Flows from '../components/flows/Flows.vue'
import Plugin from '../components/plugins/Plugin.vue'
import Settings from '../components/settings/Settings.vue'
import Templates from '../components/templates/Templates.vue'
import TemplatesEdit from '../components/templates/TemplatesEdit.vue'

export default {
    mode: 'history',
    // eslint-disable-next-line no-undef
    base: KESTRA_UI_PATH,
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

        //Templates
        { name: 'templatesList', path: '/templates', component: Templates },
        { name: 'templatesAdd', path: '/templates/new', component: TemplatesEdit },
        { name: 'templateEdit', path: '/templates/edit/:namespace/:id', component: TemplatesEdit },


        //Settings
        { name: 'settings', path: '/settings', component: Settings },
    ]
}
