<template>
    <div class="copilot-chat" data-test="copilot-chat">
        <!-- Empty state: artwork + heading + a centered composer + suggestions (Figma Default variant). -->
        <div v-if="isEmpty" class="copilot-empty">
            <div class="copilot-empty-inner">
                <div class="copilot-artwork">
                    <img :src="monogram" alt="" class="copilot-artwork-img" >
                </div>
                <KsText size="large" class="copilot-empty-title">{{ t("ai.copilot.empty.title") }}</KsText>
                <CopilotComposer
                    v-model:mode="mode"
                    :disabled="!canSend"
                    :placeholder="t('ai.copilot.emptyHelper')"
                    @submit="onSubmit"
                />
                <div class="copilot-suggestions">
                    <KsButton
                        v-for="suggestion in suggestions"
                        :key="suggestion"
                        class="copilot-suggestion"
                        :disabled="!canSend"
                        @click="onSubmit(suggestion)"
                    >
                        {{ suggestion }}
                    </KsButton>
                </div>
            </div>
        </div>

        <!-- Active conversation: scrolling transcript + composer pinned to the bottom. -->
        <template v-else>
            <KsScrollbar class="copilot-body">
                <CopilotMessage v-for="message in messages" :key="message.id" :message="message" />

                <ProposedActionCard
                    v-if="pendingConfirmation"
                    :action="pendingConfirmation"
                    :disabled="streaming"
                    @approve="confirm('APPROVE')"
                    @reject="confirm('REJECT')"
                />
            </KsScrollbar>

            <KsAlert v-if="error" type="error" class="copilot-error">
                {{ t(`ai.copilot.error.${error}`) }}
            </KsAlert>

            <div class="copilot-footer">
                <CopilotComposer v-model:mode="mode" :disabled="!canSend" @submit="onSubmit" />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import monogram from "../../../assets/monogram.svg"
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

    // Quick-start prompts shown under the empty-state composer (Figma Default variant).
    const suggestions = computed(() => [
        t("ai.copilot.suggestions.errorHandling"),
        t("ai.copilot.suggestions.unitTest"),
        t("ai.copilot.suggestions.explain"),
        t("ai.copilot.suggestions.dbt"),
    ])

    const {messages, status, streaming, error, pendingConfirmation, canSend, sendChat, confirm, cancel} = useAiChat()

    // `status` gates the composer via `canSend`; keep the lints happy that we read it.
    void status

    // Empty state shows until the first turn produces a message, a proposal, or an error.
    const isEmpty = computed(
        () => messages.value.length === 0 && !pendingConfirmation.value && !error.value,
    )

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

    .copilot-empty {
        flex: 1 1 auto;
        min-height: 0;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: var(--ks-spacing-6) var(--ks-spacing-4);
    }

    .copilot-empty-inner {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-4);
        width: 100%;
        max-width: 24rem;
    }

    .copilot-artwork {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-16);
        height: var(--ks-spacing-16);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
        box-shadow: var(--ks-shadow-surface);
    }

    .copilot-artwork-img {
        width: var(--ks-spacing-8);
        height: var(--ks-spacing-8);
    }

    .copilot-empty-title {
        font-weight: 600;
        text-align: center;
    }

    .copilot-suggestions {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        justify-content: center;
    }

    .copilot-suggestion {
        border-radius: var(--ks-radius-xl);
    }

    .copilot-body {
        flex: 1 1 auto;
        min-height: 0;
        padding: var(--ks-spacing-3);
    }

    .copilot-error {
        margin: 0 var(--ks-spacing-3);
    }

    .copilot-footer {
        padding: var(--ks-spacing-3);
        border-top: 1px solid var(--ks-border-subtle);
    }
</style>
