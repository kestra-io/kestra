<template>
    <div class="copilot-chat" data-test="copilot-chat">
        <div class="copilot-header">
            <span class="copilot-title">
                <AiIcon />&nbsp;<KsText size="default">{{ t("ai.copilot.title") }}</KsText>
            </span>
        </div>

        <KsScrollbar class="copilot-body">
            <!-- Empty state -->
            <div v-if="messages.length === 0" class="copilot-empty">
                <KsText size="large">{{ t("ai.copilot.empty.title") }}</KsText>
            </div>

            <template v-else>
                <CopilotMessage v-for="message in messages" :key="message.id" :message="message" />
            </template>

            <!-- Suspended proposal awaiting a decision -->
            <ProposedActionCard
                v-if="pendingConfirmation"
                :action="pendingConfirmation"
                :disabled="streaming"
                @approve="(reason) => confirm('APPROVE', reason)"
                @reject="(reason) => confirm('REJECT', reason)"
            />
        </KsScrollbar>

        <KsAlert v-if="error" type="error" class="copilot-error">
            {{ t(`ai.copilot.error.${error}`) }}
        </KsAlert>

        <div class="copilot-footer">
            <CopilotComposer
                v-model:mode="mode"
                :disabled="!canSend"
                @submit="onSubmit"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import AiIcon from "../AiIcon.vue"
    import CopilotMessage from "./CopilotMessage.vue"
    import CopilotComposer from "./CopilotComposer.vue"
    import ProposedActionCard from "./ProposedActionCard.vue"
    import {useAiChat} from "./useAiChat"
    import type {Mode, ScopeBinding} from "./types"

    const props = defineProps<{
        /** Initial mode; defaults to ASK. */
        initialMode?: Mode
        /** Artefact in focus, passed as `inFocus` on each turn. */
        inFocus?: ScopeBinding | null
    }>()

    const {t} = useI18n()

    const mode = ref<Mode>(props.initialMode ?? "ASK")

    const {messages, status, streaming, error, pendingConfirmation, canSend, sendChat, confirm, cancel} = useAiChat()

    // `status` gates the composer via `canSend`; keep the lints happy that we read it.
    void status

    function onSubmit(prompt: string): void {
        sendChat({prompt, mode: mode.value, inFocus: props.inFocus})
    }

    onBeforeUnmount(cancel)
</script>

<style scoped>
    .copilot-chat {
        display: flex;
        flex-direction: column;
        height: 100%;
        min-height: 0;
    }

    .copilot-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--ks-spacing-3);
        border-bottom: 1px solid var(--ks-border-subtle);
    }

    .copilot-title {
        display: inline-flex;
        align-items: center;
    }

    .copilot-body {
        flex: 1 1 auto;
        min-height: 0;
        padding: var(--ks-spacing-3);
    }

    .copilot-empty {
        display: flex;
        align-items: center;
        justify-content: center;
        height: 100%;
        text-align: center;
        color: var(--ks-text-secondary);
    }

    .copilot-error {
        margin: 0 var(--ks-spacing-3);
    }

    .copilot-footer {
        padding: var(--ks-spacing-3);
        border-top: 1px solid var(--ks-border-subtle);
    }
</style>
