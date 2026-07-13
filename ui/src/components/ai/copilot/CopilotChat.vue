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
                    <CopilotThreadList :activeId="thread?.uid" @select="onSelectThread" />
                </template>
            </KsDropdown>
        </div>

        <!-- AI unavailable: the backend has no configured provider (503). -->
        <div v-if="unavailable" class="copilot-unavailable" data-test="copilot-unavailable">
            <KsIcon class="copilot-unavailable-icon">
                <RobotOffOutline />
            </KsIcon>
            <KsText class="copilot-unavailable-title">{{ t("ai.copilot.unavailable.title") }}</KsText>
            <KsText size="small" class="copilot-unavailable-detail">{{ t("ai.copilot.unavailable.detail") }}</KsText>
            <KsButton size="small" data-test="copilot-unavailable-retry" @click="retry">
                {{ t("ai.copilot.unavailable.retry") }}
            </KsButton>
        </div>

        <!-- Empty state: artwork + heading + a centered composer + suggestions (Figma Default variant). -->
        <div v-else-if="isEmpty" class="copilot-empty">
            <div class="copilot-empty-inner">
                <div class="copilot-artwork">
                    <img :src="logo" alt="" class="copilot-artwork-img" >
                </div>
                <KsText size="large" class="copilot-empty-title">{{ t("ai.copilot.empty.title") }}</KsText>
                <CopilotComposer
                    ref="emptyComposer"
                    v-model="composerText"
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
                    @reject="onReject"
                />

                <!-- Anchor the auto-scroll follows as new content streams in. -->
                <div ref="bottomAnchor" class="copilot-scroll-anchor" />
            </KsScrollbar>

            <KsAlert v-if="error" type="error" class="copilot-error">
                {{ t(`ai.copilot.error.${error}`) }}
            </KsAlert>

            <div class="copilot-footer">
                <CopilotComposer
                    ref="footerComposer"
                    v-model="composerText"
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
    import {ref, computed, nextTick, watch, onBeforeUnmount, onMounted} from "vue"
    import {useI18n} from "vue-i18n"
    import Plus from "vue-material-design-icons/Plus.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import RobotOffOutline from "vue-material-design-icons/RobotOffOutline.vue"
    import * as AiApi from "@kestra-io/kestra-sdk/ai"
    import type {AiControllerAiProviderResponse} from "@kestra-io/kestra-sdk"
    import logo from "../../../assets/copilot-illustration.png"
    import CopilotMessage from "./CopilotMessage.vue"
    import CopilotComposer from "./CopilotComposer.vue"
    import CopilotThinking from "./CopilotThinking.vue"
    import CopilotThreadList from "./CopilotThreadList.vue"
    import ProposedActionCard from "./ProposedActionCard.vue"
    import {useAiChat} from "./useAiChat"
    import type {Mode, ScopeBinding} from "./types"
    import {useMiscStore} from "override/stores/misc"

    const props = defineProps<{
        /** Initial mode; defaults to EDIT. */
        initialMode?: Mode
        /** Artefact in focus, passed as `inFocus` on each turn. */
        inFocus?: ScopeBinding | null
    }>()

    const {t} = useI18n()
    const miscStore = useMiscStore()

    const mode = ref<Mode>(props.initialMode ?? "EDIT")

    // Shared composer text (both the empty-state and footer composers bind it), so an external
    // entry point can seed a prompt via the misc store (see consumeSeededPrompt).
    const composerText = ref("")
    const emptyComposer = ref<InstanceType<typeof CopilotComposer> | null>(null)

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

    const {thread, messages, status, streaming, error, pendingConfirmation, unavailable, canSend, sendChat, confirm, cancel, reset, retry, loadThread} = useAiChat()

    // Switch to a thread picked from the Recents list (rehydrates history + resumes any pending action).
    function onSelectThread(threadId: string): void {
        loadThread(threadId)
    }

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

    // Keep the transcript pinned to the bottom as content arrives: new messages, streamed
    // tokens (last message's content grows), the thinking indicator, or a proposed-action card.
    const bottomAnchor = ref<HTMLElement | null>(null)

    // A primitive that changes on any of those, so the watcher fires without a deep watch.
    const scrollSignal = computed(() => {
        const last = messages.value[messages.value.length - 1]
        return `${messages.value.length}|${last?.content?.length ?? 0}|${pendingConfirmation.value ? 1 : 0}|${thinking.value ? 1 : 0}`
    })

    watch(scrollSignal, () => nextTick(() => bottomAnchor.value?.scrollIntoView?.({block: "end"})))

    // "Reply to revise": decline the proposal, then hand control back to the composer so the
    // user can type what to change (the next turn re-plans). Focus once the turn resolves and
    // the composer is enabled again.
    const footerComposer = ref<InstanceType<typeof CopilotComposer> | null>(null)

    async function onReject(): Promise<void> {
        await confirm("REJECT")
        await nextTick()
        footerComposer.value?.focus()
    }

    // Seeded prompts: an entry point (e.g. "Fix with AI") calls miscStore.promptCopilot(text),
    // which opens this tab and stashes the text. Prefill the composer with it and focus, then
    // clear the store so it doesn't re-seed on the next open. Runs on mount (drawer just opened)
    // and via a watcher (drawer already open / kept alive).
    async function consumeSeededPrompt(): Promise<void> {
        const seeded = miscStore.copilotPrompt
        if (!seeded) return
        composerText.value = seeded
        miscStore.copilotPrompt = null
        await nextTick()
        ;(isEmpty.value ? emptyComposer.value : footerComposer.value)?.focus()
    }

    onMounted(consumeSeededPrompt)
    watch(() => miscStore.copilotPrompt, (value) => {
        if (value) consumeSeededPrompt()
    })

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

    /* AI-unavailable state (no provider configured): centered message + retry, no composer. */
    .copilot-unavailable {
        flex: 1 1 auto;
        min-height: 0;
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-6) var(--ks-spacing-5);
        text-align: center;
    }

    .copilot-unavailable-icon {
        font-size: var(--ks-spacing-8);
        color: var(--ks-text-secondary);
    }

    .copilot-unavailable-title {
        font-weight: 600;
    }

    .copilot-unavailable-detail {
        --kel-text-color: var(--ks-text-secondary);
        max-width: 22rem;
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

    /* Wider horizontal breathing room for the transcript; the footer/error keep the same
       inset so bubbles and the composer stay aligned to one gutter. */
    .copilot-body {
        flex: 1 1 auto;
        min-height: 0;
        padding: var(--ks-spacing-3) var(--ks-spacing-5);
    }

    .copilot-error {
        margin: 0 var(--ks-spacing-5);
    }

    .copilot-footer {
        padding: var(--ks-spacing-3) var(--ks-spacing-5);
        border-top: 1px solid var(--ks-border-subtle);
    }
</style>
