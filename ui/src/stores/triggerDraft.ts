import {defineStore} from "pinia";
import {ref} from "vue";

export interface TriggerDraft {
    namespace: string;
    flowId: string;
    triggerYaml: string;
}

/**
 * Carries a pre-configured trigger YAML block from the tenant-level "Add Trigger" modal
 * to the flow editor. The flow editor reads the draft on mount, inserts it into the flow
 * YAML, and clears the draft. Keyed by the latest draft (one in-flight draft at a time).
 */
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

    function clear() {
        draft.value = undefined;
    }

    return {
        draft,
        setDraft,
        consumeDraft,
        clear,
    };
});
