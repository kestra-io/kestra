import {defineStore} from "pinia";
import {ref} from "vue";
import {Message} from "../components/ErrorToast.vue";
import * as sdk from "../generated/kestra-api/ks-sdk.gen";

interface GuidedProperties {
    tourStarted: boolean;
    manuallyContinue: boolean;
    template: any;
    saveFlow?: boolean;
    glowExecuteButton?: boolean;
}

export const useCoreStore = defineStore("core", () => {
    const message = ref<Message>()
    const error = ref<any>()
    const guidedProperties = ref<GuidedProperties>({
        tourStarted: false,
        manuallyContinue: false,
        template: undefined,
    })
    const monacoYamlConfigured = ref(false)
    const tutorialFlows = ref<any[]>([]);

    async function readTutorialFlows() {
        const response = await sdk.Flows.listFlowsByNamespace({
            namespace: "tutorials",
        })
        tutorialFlows.value = response.data ?? [];
        return response.data;
    }

    return {
        message,
        error,
        guidedProperties,
        monacoYamlConfigured,
        tutorialFlows,
        readTutorialFlows,
    }
});
