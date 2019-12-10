import Vue from 'vue'
import Vuex from 'vuex'
import core from './core'
import settings from './settings'
import auth from './auth'
import flow from './flow'
import namespace from './namespaces'

Vue.use(Vuex)


export default new Vuex.Store({
    modules: {
        core,
        auth,
        settings,
        flow,
        namespace
    }
})