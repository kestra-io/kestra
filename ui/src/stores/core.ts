import {defineStore} from "pinia"
import {ref} from "vue"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {Message} from "../components/ErrorToast.vue"
import {TUTORIAL_NAMESPACE} from "../utils/constants"
import {Flow} from "./flow"

export interface FailedRequest {
    status: number
    method: string
    url: string
    message?: string
}

export const useCoreStore = defineStore("core", () => {
    const message = ref<Message>()
    const error = ref<any>()
    const failedRequest = ref<FailedRequest>()

    async function readTutorialFlows() {
        const flows = await FlowsAPI.listFlowsByNamespace({namespace: TUTORIAL_NAMESPACE}) as Flow[]
        return flows
    }

    return {
        message,
        error,
        failedRequest,
        readTutorialFlows,
    }
})
