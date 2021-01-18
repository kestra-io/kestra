import {createLocalVue} from "@vue/test-utils"
import BootstrapVue from "bootstrap-vue"
import Vuex from "vuex"

const localVue = createLocalVue()

localVue.use(BootstrapVue)
localVue.use(Vuex)

export default localVue