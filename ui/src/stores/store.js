import Vue from 'vue'
import Vuex from 'vuex'
import core from './core'
import auth from './auth'
import flow from './flow'
import settings from './settings'

Vue.use(Vuex)


export default new Vuex.Store({
    modules: {
        core,
        auth,
        flow,
        settings
    }
})