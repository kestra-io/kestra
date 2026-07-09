<template>
    <div class="copilot-chat" data-test="copilot-chat">
        <!-- Thread controls: start a new chat, browse recents (recents is a BE-pending placeholder). -->
        <div class="copilot-topbar">
            <KsButton size="small" class="copilot-topbar-pill" data-test="copilot-new-chat" @click="reset">
                {{ t("ai.copilot.newChat") }}
                <Plus :size="16" />
            </KsButton>
            <KsDropdown trigger="click" data-test="copilot-recents">
                <KsButton size="small" class="copilot-topbar-pill">
                    {{ t("ai.copilot.recents") }}
                    <ChevronDown :size="16" />
                </KsButton>
                <template #dropdown>
                    <KsDropdownMenu>
                        <KsDropdownItem disabled>{{ t("ai.copilot.recentsEmpty") }}</KsDropdownItem>
                    </KsDropdownMenu>
                </template>
            </KsDropdown>
        </div>

        <!-- Empty state: artwork + heading + a centered composer + suggestions (Figma Default variant). -->
        <div v-if="isEmpty" class="copilot-empty">
            <div class="copilot-empty-inner">
                <div class="copilot-artwork">
                    <img :src="logo" alt="" class="copilot-artwork-img" >
                </div>
                <KsText size="large" class="copilot-empty-title">{{ t("ai.copilot.empty.title") }}</KsText>
                <CopilotComposer
                    v-model:mode="mode"
                    v-model:provider="selectedProvider"
                    :providers="providers"
                    :disabled="!canSend"
                    :placeholder="t('ai.copilot.emptyHelper')"
                    :rows="3"
                    @submit="onSubmit"
                />
                <div class="copilot-suggestions">
                    <KsButton
                        v-for="suggestion in suggestions"
                        :key="suggestion"
                        size="small"
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

                <CopilotThinking v-if="thinking" />

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
                <CopilotComposer
                    v-model:mode="mode"
                    v-model:provider="selectedProvider"
                    :providers="providers"
                    :disabled="!canSend"
                    @submit="onSubmit"
                />
            </div>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onBeforeUnmount, onMounted} from "vue"
    import {useI18n} from "vue-i18n"
    import Plus from "vue-material-design-icons/Plus.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import * as AiApi from "@kestra-io/kestra-sdk/ai"
    import type {AiControllerAiProviderResponse} from "@kestra-io/kestra-sdk"
    import logo from "../../../assets/copilot-illustration.png"
    import CopilotMessage from "./CopilotMessage.vue"
    import CopilotComposer from "./CopilotComposer.vue"
    import CopilotThinking from "./CopilotThinking.vue"
    import ProposedActionCard from "./ProposedActionCard.vue"
    import {useAiChat} from "./useAiChat"
    import type {Mode, ScopeBinding} from "./types"

    const props = defineProps<{
        /** Initial mode; defaults to EDIT. */
        initialMode?: Mode
        /** Artefact in focus, passed as `inFocus` on each turn. */
        inFocus?: ScopeBinding | null
    }>()

    const {t} = useI18n()

    const mode = ref<Mode>(props.initialMode ?? "EDIT")

    // Available AI providers (same endpoint the previous copilot used); the selected one is
    // sent as providerId on each turn. Falls back to the server default when unset.
    const providers = ref<AiControllerAiProviderResponse[]>([])
    const selectedProvider = ref<string>()

    onMounted(async () => {
        try {
            const list = await AiApi.providers()
            providers.value = list ?? []
            selectedProvider.value = (providers.value.find((p) => p.isDefault) ?? providers.value[0])?.id
        } catch {
            // No provider list (e.g. AI unavailable) — the composer just omits the picker.
        }
    })

    // Quick-start prompts shown under the empty-state composer (Figma Default variant).
    const suggestions = computed(() => [
        t("ai.copilot.suggestions.errorHandling"),
        t("ai.copilot.suggestions.unitTest"),
        t("ai.copilot.suggestions.explain"),
        t("ai.copilot.suggestions.dbt"),
    ])

    const {messages, status, streaming, error, pendingConfirmation, canSend, sendChat, confirm, cancel, reset} = useAiChat()

    // `status` gates the composer via `canSend`; keep the lints happy that we read it.
    void status

    // Empty state shows until the first turn produces a message, a proposal, or an error.
    const isEmpty = computed(
        () => messages.value.length === 0 && !pendingConfirmation.value && !error.value,
    )

    // "Thinking…" placeholder while the model is working but hasn't produced its next output
    // yet — i.e. right after the user's turn or after a tool result. Hidden while tokens are
    // actively streaming into an assistant bubble or a tool is running (both have their own UI).
    const thinking = computed(() => {
        if (!streaming.value) return false
        const last = messages.value[messages.value.length - 1]
        if (!last) return true
        if (last.role === "ASSISTANT" && last.type === "TEXT") return false
        if (last.type === "TOOL_CALL") return false
        return true
    })

    function onSubmit(prompt: string): void {
        sendChat({prompt, mode: mode.value, inFocus: props.inFocus, providerId: selectedProvider.value})
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

    .copilot-topbar {
        display: flex;
        /* Figma: pills sit 4px apart in a p-8 row. */
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
    }

    /*
        Figma "New chat" / "Recents": compact bg-tag pills — regular 12px label + 16px icon,
        4px gap, px-8. Mirrors the composer mode pill so both controls read as the same family.
    */
    .copilot-topbar-pill {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        /* Solid raised-surface grey (Figma pill ≈ #1d1d21) — reads darker and more
           defined than the translucent bg-tag, consistently across dark themes. */
        background: var(--ks-bg-elevated);
        border: none;
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-sm);
        font-weight: 400;
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
        max-width: 30rem;
    }

    .copilot-artwork {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .copilot-artwork-img {
        width: var(--ks-spacing-16);
        height: var(--ks-spacing-16);
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
        width: 100%;
    }

    /* Figma "tag-btn": subtle surface pill, subtle border, semibold secondary label. */
    .copilot-suggestion {
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        color: var(--ks-text-secondary);
        font-weight: 600;
        padding: var(--ks-spacing-2) var(--ks-spacing-4);
        /* Fully-rounded "stadium" pill (rounded-111) — a shape, not a theme radius. */
        border-radius: 999px;
        box-shadow: var(--ks-shadow-element);
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
