import {createLocalVue} from "@vue/test-utils"
import BootstrapVue from "bootstrap-vue"
import Vuex from "vuex"
import VueMoment from "vue-moment"

const localVue = createLocalVue()

localVue.use(BootstrapVue)
localVue.use(Vuex)
localVue.use(VueMoment)

export default localVue