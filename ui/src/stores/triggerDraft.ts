import {defineStore} from "pinia";
import {ref} from "vue";

export interface TriggerDraft {
    namespace: string;
    flowId: string;
    triggerYaml: string;
}

export const useTriggerDraftStore = defineStore("triggerDraft", () => {
    const draft = ref<TriggerDraft | undefined>();

    function setDraft(value: TriggerDraft) {
        draft.value = value;
    }

    function consumeDraft(namespace: string, flowId: string): TriggerDraft | undefined {
        const current = draft.value;
        if (!current || current.namespace !== namespace || current.flowId !== flowId) {
            return undefined;
        }
        draft.value = undefined;
        return current;
    }

    return {setDraft, consumeDraft};
});
