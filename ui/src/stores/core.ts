import {defineStore} from "pinia"
import {ref} from "vue"
// POC: sourced from the in-repo, same-commit OSS SDK instead of the published npm package
// (see ui/packages/kestra-sdk-oss/README.md).
import * as FlowsAPI from "@kestra-io/kestra-sdk-oss/flows"
import {Message} from "../components/ErrorToast.vue"
import {TUTORIAL_NAMESPACE} from "../utils/constants"
import {Flow} from "./flow"

export const useCoreStore = defineStore("core", () => {
    const message = ref<Message>()
    const error = ref<any>()
    const monacoYamlConfigured = ref(false)
    const tutorialFlows = ref<Flow[]>([])

    async function readTutorialFlows() {
        const flows = await FlowsAPI.listFlowsByNamespace({namespace: TUTORIAL_NAMESPACE}) as Flow[]
        tutorialFlows.value = flows
        return flows
    }

    return {
        message,
        error,
        monacoYamlConfigured,
        tutorialFlows,
        readTutorialFlows,
    }
})
