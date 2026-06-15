<template>
    <div ref="rootEl" class="ai-copilot-wrapper">
        <AITriggerButton
            v-if="sticky && aiCopilotAllowed"
            ref="triggerBtn"
            class="no-code-ai-trigger"
            :show="true"
            :opened="aiCopilotOpened"
            @click="openAiCopilot"
        />

        <slot
            :aiCopilotAllowed="aiCopilotAllowed"
            :aiCopilotOpened="aiCopilotOpened"
            :openAiCopilot="openAiCopilot"
            :closeAiCopilot="closeAiCopilot"
        />

        <Transition name="copilot-slide">
            <AiCopilot
                v-if="aiCopilotOpened"
                ref="copilotEl"
                class="position-absolute prompt ai-copilot-popup"
                @close="closeAiCopilot"
                :flow="flow"
                :conversationId="conversationId"
                :namespace="namespace"
                @generated-yaml="onGeneratedYaml"
                :generationType="generationType"
            />
        </Transition>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {onClickOutside} from "@vueuse/core"
    import AiCopilot from "./AiCopilot.vue"
    import AITriggerButton from "./AITriggerButton.vue"
    import {useAuthStore} from "override/stores/auth"
    import {useApiStore} from "../../stores/api"
    import {useMiscStore} from "override/stores/misc"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import * as Utils from "../../utils/utils"
    import {aiGenerationTypes, AI_PROVIDER_POPPER_CLASS} from "../../utils/constants"
    import type {AiGenerationType} from "../../utils/constants"

    const props = withDefaults(defineProps<{
        flow: string;
        generationType: AiGenerationType;
        namespace?: string;
        sticky?: boolean;
    }>(), {
        namespace: undefined,
        sticky: false,
    })

    const emit = defineEmits<{
        (e: "generated-yaml", yaml: string): void;
    }>()

    const route = useRoute()
    const router = useRouter()
    const authStore = useAuthStore()
    const apiStore = useApiStore()
    const miscStore = useMiscStore()

    const rootEl = ref<HTMLDivElement>()
    const copilotEl = ref<InstanceType<typeof AiCopilot>>()
    const triggerBtn = ref<InstanceType<typeof AITriggerButton>>()
    const aiCopilotOpened = ref(false)
    const conversationId = ref<string>(Utils.uid())

    onClickOutside(
        computed(() => copilotEl.value?.$el),
        () => { if (aiCopilotOpened.value) closeAiCopilot() },
        {ignore: [computed(() => triggerBtn.value?.$el), `.${AI_PROVIDER_POPPER_CLASS}`]},
    )

    const aiCopilotAllowed = computed(() => {
        if (!authStore.user?.hasAnyActionOnAnyNamespace(resource.COPILOT, action.USE)) {
            return false
        }
        if (props.generationType === aiGenerationTypes.APP || props.generationType === aiGenerationTypes.TEST) {
            return miscStore.configs?.isAiApiKeyConfigured === true
        }
        return true
    })

    function openAiCopilot() {
        apiStore.posthogEvents({
            type: "AI_COPILOT",
            action: "open_click",
        })
        aiCopilotOpened.value = true
    }

    function closeAiCopilot() {
        aiCopilotOpened.value = false
        clearAiQueryParam()
    }

    function clearAiQueryParam() {
        if (route.query.ai) {
            router.replace({
                name: route.name,
                params: route.params,
                query: {...route.query, ai: undefined},
            })
        }
    }

    function resetConversation() {
        conversationId.value = Utils.uid()
    }

    function onGeneratedYaml(yaml: string) {
        emit("generated-yaml", yaml)
        aiCopilotOpened.value = false
    }

    defineExpose({
        aiCopilotOpened,
        openAiCopilot,
        closeAiCopilot,
        resetConversation,
        rootEl,
    })
</script>

<style scoped lang="scss">
    .ai-copilot-wrapper {
        position: relative;
        height: 100%;
        width: 100%;
    }

    .prompt {
        bottom: 10%;
        width: calc(100% - 5rem);
        left: 3rem;
        max-width: 700px;
        background-color: var(--ks-bg-surface);
        box-shadow: 0 2px 4px 0 var(--ks-shadow-element);
    }

    .no-code-ai-trigger {
        position: sticky;
        top: 0.75rem;
        float: right;
        margin-right: 0.75rem;
        z-index: 10;
    }

    :slotted(.no-code-content-with-ai) {
        padding-top: calc(1.5rem + 32px) !important; // 0.75rem top offset + button height + 0.75rem gap
    }

    .ai-copilot-popup {
        z-index: 1001;
        transform-origin: center bottom;
    }

    .copilot-slide-enter-active {
        transition: transform 0.45s cubic-bezier(0.2, 0.8, 0.2, 1), opacity 0.15s ease;
    }

    .copilot-slide-leave-active {
        transition: transform 0.35s cubic-bezier(0.4, 0.0, 1, 1);
    }

    .copilot-slide-enter-from {
        opacity: 0;
        transform: scaleX(0.85);
    }

    .copilot-slide-leave-to {
        transform: scaleX(0.95);
    }

    @media (max-width: 768px) {
        .prompt {
            width: calc(100% - 2rem);
            left: 1rem;
            bottom: 5%;
        }
    }

    @media (max-width: 480px) {
        .prompt {
            width: calc(100% - 1rem);
            left: 0.5rem;
            bottom: 2%;
        }
    }
</style>
