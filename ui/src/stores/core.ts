import {defineStore} from "pinia"
import {ref} from "vue"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {Message} from "../components/ErrorToast.vue"
import {TUTORIAL_NAMESPACE} from "../utils/constants"
import {Flow} from "./flow"

export const useCoreStore = defineStore("core", () => {
    const message = ref<Message>()
    const error = ref<any>()

    async function readTutorialFlows() {
        const flows = await FlowsAPI.listFlowsByNamespace({namespace: TUTORIAL_NAMESPACE}) as Flow[]
        return flows
    }

    return {
        message,
        error,
        readTutorialFlows,
    }
})
