import {defineStore} from "pinia"
import {ref} from "vue"

export interface TriggerDraft {
    namespace: string;
    flowId: string;
    triggerYaml: string;
}

export const useTriggerDraftStore = defineStore("triggerDraft", () => {
    const draft = ref<TriggerDraft>()

    const setDraft = (value: TriggerDraft) => draft.value = value

    const consumeDraft = (namespace: string, flowId: string): TriggerDraft | undefined => {
        const current = draft.value
        if (current?.namespace === namespace && current?.flowId === flowId) {
            draft.value = undefined
            return current
        }
    }

    return {setDraft, consumeDraft}
})
