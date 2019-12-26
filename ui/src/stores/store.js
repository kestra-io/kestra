import Vue from 'vue'
import Vuex from 'vuex'
import core from './core'
import settings from './settings'
import auth from './auth'
import flow from './flow'
import execution from './executions'
import namespace from './namespaces'
import layout from './layout'

Vue.use(Vuex)


export default new Vuex.Store({
    modules: {
        core,
        auth,
        settings,
        flow,
        execution,
        namespace,
        layout
    }
})