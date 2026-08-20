<template>
    <div class="copilot-chat" :class="`copilot-chat--${layout}`" data-test="copilot-chat">
        <!-- Thread controls: start a new chat; the Recents list (switch / rename / delete) is EE-only,
             rendered by the CopilotThreadControls override (a no-op in OSS). -->
        <div class="copilot-topbar">
            <KsButton v-if="!isFreshChat" size="small" class="copilot-topbar-pill" data-test="copilot-new-chat" @click="reset">
                {{ $t("ai.copilot.newChat") }}
                <Plus :size="16" />
            </KsButton>
            <CopilotThreadControls :activeId="thread?.uid" @select="onSelectThread" />
        </div>

        <!-- AI unavailable: the backend has no configured provider (503). -->
        <div v-if="unavailable" class="copilot-unavailable" data-test="copilot-unavailable">
            <KsIcon class="copilot-unavailable-icon">
                <RobotOffOutline />
            </KsIcon>
            <KsText class="copilot-unavailable-title">{{ $t("ai.copilot.unavailable.title") }}</KsText>
            <KsText size="small" class="copilot-unavailable-detail">{{ $t("ai.copilot.unavailable.detail") }}</KsText>
            <KsButton size="small" data-test="copilot-unavailable-retry" @click="retry">
                {{ $t("ai.copilot.unavailable.retry") }}
            </KsButton>
        </div>

        <div v-else-if="isEmpty" class="copilot-empty">
            <div class="copilot-empty-inner">
                <div class="copilot-artwork">
                    <img :src="logo" alt="" class="copilot-artwork-img" >
                </div>
                <KsText size="large" class="copilot-empty-title">{{ $t("ai.copilot.empty.title") }}</KsText>
                <CopilotContextChip v-if="activeScope" :scope="activeScope" @remove="removeContext" />
                <CopilotComposer
                    ref="emptyComposer"
                    v-model="composerText"
                    v-model:mode="mode"
                    v-model:provider="selectedProvider"
                    :providers="providers"
                    :disabled="!canSend"
                    :placeholder="$t('ai.copilot.emptyHelper')"
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

                <CopilotHelp v-if="layout === 'page'" />
            </div>
        </div>

        <template v-else>
            <!-- The transcript is a polite live region so a screen reader announces streamed tokens
                 and new messages as they arrive; `aria-busy` marks it working while a turn streams. -->
            <KsScrollbar
                class="copilot-body"
                role="log"
                aria-live="polite"
                :aria-busy="streaming ? 'true' : 'false'"
            >
                <CopilotMessage
                    v-for="message in messages"
                    :key="message.id"
                    :message="message"
                    :isPending="message.id === pendingProposalMessageId"
                    :isRunning="message.id === runningToolCallId"
                />

                <CopilotThinking v-if="working" :phase="workPhase" />

                <ProposedActionCard
                    v-if="pendingConfirmation"
                    :action="pendingConfirmation"
                    :disabled="streaming"
                    @approve="confirm('APPROVE', undefined, selectedProvider)"
                    @reject="onReject"
                />

                <!-- Anchor the auto-scroll follows as new content streams in. -->
                <div ref="bottomAnchor" class="copilot-scroll-anchor" />
            </KsScrollbar>

            <!-- Insets via wrapper padding, not a margin on the alert: KsAlert is width:100%, so a
                 horizontal margin would push it past the panel (100% + margin) and overflow. -->
            <div v-if="error || errorDetail || notice" class="copilot-banner">
                <KsAlert v-if="error || errorDetail" type="error" role="alert" data-test="copilot-error">
                    {{ errorDetail || $t(`ai.copilot.error.${error}`) }}
                </KsAlert>
                <KsAlert v-else-if="notice" type="warning" role="status" data-test="copilot-notice">
                    <div class="copilot-notice-body">
                        <span>{{ $t(`ai.copilot.notice.${notice}`) }}</span>
                        <KsButton size="small" data-test="copilot-notice-retry" @click="retryLastTurn">
                            {{ $t("ai.copilot.notice.retry") }}
                        </KsButton>
                    </div>
                </KsAlert>
            </div>

            <div class="copilot-footer">
                <CopilotContextChip v-if="activeScope" :scope="activeScope" @remove="removeContext" />
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
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import Plus from "vue-material-design-icons/Plus.vue"
    import RobotOffOutline from "vue-material-design-icons/RobotOffOutline.vue"
    import * as AiApi from "@kestra-io/kestra-sdk/ai"
    import type {AgentMode, AiControllerAiProviderResponse} from "@kestra-io/kestra-sdk"
    import logo from "../../../assets/copilot-illustration.png"
    import CopilotMessage from "./CopilotMessage.vue"
    import CopilotComposer from "./CopilotComposer.vue"
    import CopilotThinking from "./CopilotThinking.vue"
    import ProposedActionCard from "./ProposedActionCard.vue"
    import CopilotContextChip from "./CopilotContextChip.vue"
    import CopilotHelp from "./CopilotHelp.vue"
    import CopilotThreadControls from "override/components/ai/copilot/CopilotThreadControls.vue"
    import {useAiChat} from "./useAiChat"
    import {scopeFromRoute, scopeToContext, CONTEXT_PART_I18N, CONTEXT_PRIMARY} from "./routeScope"
    import type {ScopeBinding, ContextPart} from "./types"
    import {useMiscStore} from "override/stores/misc"

    const props = withDefaults(defineProps<{
        /** Initial mode; defaults to EDIT. */
        initialMode?: AgentMode
        /** Scope the user is focused on; sent as `additionalContext` on each turn. */
        inFocus?: ScopeBinding | null
        /** Surface variant: the right-side "dock" (default) or the full-width "page" home. */
        layout?: "dock" | "page"
    }>(), {layout: "dock"})

    const {t} = useI18n()
    const route = useRoute()
    const miscStore = useMiscStore()

    const mode = ref<AgentMode>(props.initialMode ?? "EDIT")

    // When the copilot opens on a flow / execution / namespace page, send that page as `inFocus` so
    // the agent knows what the user is looking at; recomputed as the route changes. An explicit
    // `inFocus` prop still wins.
    const routeInFocus = computed<ScopeBinding | null>(() => props.inFocus ?? scopeFromRoute(route))

    // The user can dismiss individual context pills to run a turn without that resource. Dismissals
    // are re-armed whenever the focused resource changes (navigating to a new page re-attaches scope).
    const dismissedParts = ref(new Set<ContextPart>())
    const activeScope = computed<ScopeBinding | null>(() => {
        const scope = routeInFocus.value
        if (!scope) return null
        const keep = (part: ContextPart) => (dismissedParts.value.has(part) ? undefined : scope[part])
        const effective: ScopeBinding = {
            kind: scope.kind,
            namespace: keep("namespace"),
            flowId: keep("flowId"),
            executionId: keep("executionId"),
            dashboardId: keep("dashboardId"),
            appId: keep("appId"),
            testId: keep("testId"),
            blueprintId: keep("blueprintId"),
            pluginId: keep("pluginId"),
        }
        // Once every focused resource is dismissed there's nothing left to show or send.
        return Object.entries(effective).some(([field, value]) => field !== "kind" && value) ? effective : null
    })
    // Announce focus changes in the transcript (display-only) and re-arm dismissals for the
    // newly-focused resource. `noteContext` no-ops until a conversation has started, so the empty
    // state stays clean.
    let previousFocus: ScopeBinding | null = routeInFocus.value
    watch(
        () => JSON.stringify(routeInFocus.value),
        () => {
            const current = routeInFocus.value
            dismissedParts.value = new Set()
            const primary = current ? CONTEXT_PRIMARY[current.kind] : null
            const value = primary ? current?.[primary] : undefined
            if (primary && value && value !== previousFocus?.[primary]) {
                noteContext({action: "added", noun: CONTEXT_PART_I18N[primary].noun, id: value})
            }
            previousFocus = current
        },
    )

    /** Dismiss a single context pill and note its removal in the transcript. */
    function removeContext(part: ContextPart): void {
        const value = routeInFocus.value?.[part]
        dismissedParts.value.add(part)
        if (value) noteContext({action: "removed", noun: CONTEXT_PART_I18N[part].noun, id: value})
    }

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

    const {thread, messages, status, streaming, error, errorDetail, notice, pendingConfirmation, unavailable, canSend, sendChat, confirm, cancel, reset, retry, retryLastTurn, loadThread, restoreThread, noteContext} = useAiChat()

    // Restore the last conversation on open (threads are persisted server-side); harmless no-op if none.
    onMounted(() => { restoreThread() })

    /** Switch to a thread picked from the (EE) Recents list — rehydrates its transcript + pending action. */
    function onSelectThread(threadId: string): void {
        loadThread(threadId)
    }

    // `status` gates the composer via `canSend`; keep the lints happy that we read it.
    void status

    // Empty state shows until the first turn produces a message, a proposal, or an error.
    const isEmpty = computed(
        () => messages.value.length === 0 && !pendingConfirmation.value && !error.value && !notice.value,
    )

    // "New chat" resets the conversation - so it's hidden when we're already on a fresh, empty
    // chat with no thread to clear.
    const isFreshChat = computed(() => isEmpty.value && !thread.value)

    // The pending proposal is always the last PROPOSED_ACTION message; the interactive card below the
    // transcript renders it, so CopilotMessage skips it inline (past proposals still render read-only).
    const pendingProposalMessageId = computed(() =>
        pendingConfirmation.value
            ? [...messages.value].reverse().find((m) => m.type === "PROPOSED_ACTION")?.id ?? null
            : null,
    )

    // The working indicator (animated Kestra mark) persists across the whole turn, switching movement
    // by phase — "thinking" before any output, "answering" while tokens stream, an "end" gather when
    // the turn closes. It stays hidden while a tool runs — the tool strip owns that UI.
    const lastMessage = computed(() => messages.value[messages.value.length - 1])

    // While a turn streams and the latest message is a tool call, that tool is still executing (its
    // result hasn't arrived) — flag it so its strip shows a spinner instead of sitting blank.
    const runningToolCallId = computed(() =>
        streaming.value && lastMessage.value?.type === "TOOL_CALL" ? lastMessage.value.id : null,
    )

    const answering = computed(
        () => streaming.value && lastMessage.value?.role === "ASSISTANT" && lastMessage.value?.type === "TEXT",
    )
    const thinking = computed(() => {
        if (!streaming.value) return false
        const last = lastMessage.value
        if (!last) return true
        if (last.role === "ASSISTANT" && last.type === "TEXT") return false
        if (last.type === "TOOL_CALL") return false
        return true
    })

    // A short window after streaming stops so the "end" gather animation can play before the
    // indicator unmounts. Re-armed to false the moment a new turn starts streaming.
    const ending = ref(false)
    let endTimer: ReturnType<typeof setTimeout> | undefined
    watch(streaming, (now, was) => {
        clearTimeout(endTimer)
        if (was && !now) {
            ending.value = true
            // Covers the full end sequence: dots gather + mark bloom (~0.7s), a 3s hold, then the fade.
            endTimer = setTimeout(() => (ending.value = false), 4300)
        } else if (now) {
            ending.value = false
        }
    })
    onBeforeUnmount(() => clearTimeout(endTimer))

    const working = computed(() => ending.value || thinking.value || answering.value)
    const workPhase = computed<"thinking" | "answering" | "end">(() =>
        ending.value ? "end" : answering.value ? "answering" : "thinking",
    )

    function onSubmit(prompt: string): void {
        sendChat({prompt, mode: mode.value, additionalContext: scopeToContext(activeScope.value), providerId: selectedProvider.value})
    }

    // Keep the transcript pinned to the bottom as content arrives: new messages, streamed
    // tokens (last message's content grows), the thinking indicator, or a proposed-action card.
    const bottomAnchor = ref<HTMLElement | null>(null)

    // A primitive that changes on any of those, so the watcher fires without a deep watch.
    const scrollSignal = computed(() => {
        const last = messages.value[messages.value.length - 1]
        return `${messages.value.length}|${last?.content?.length ?? 0}|${pendingConfirmation.value ? 1 : 0}|${working.value ? 1 : 0}`
    })

    watch(scrollSignal, () => nextTick(() => bottomAnchor.value?.scrollIntoView?.({block: "end"})))

    // "Reply to revise": decline the proposal, then hand control back to the composer so the
    // user can type what to change (the next turn re-plans). Focus once the turn resolves and
    // the composer is enabled again.
    const footerComposer = ref<InstanceType<typeof CopilotComposer> | null>(null)

    async function onReject(): Promise<void> {
        await confirm("REJECT", undefined, selectedProvider.value)
        await nextTick()
        footerComposer.value?.focus()
    }

    // Seeded prompts: an entry point (e.g. "Fix with AI") stashes text via miscStore, which opens
    // this tab. Prefill the composer with it and focus, then clear the store so it doesn't re-seed —
    // run on mount (drawer just opened) and via a watcher (already open / kept alive).
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
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        /* Solid raised-surface grey (Figma pill ≈ #1d1d21) — reads darker and more
           defined than the translucent bg-tag, consistently across dark themes. */
        background: var(--ks-bg-elevated);
        border: none;
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-sm);
        font-weight: 400;
        transition: background 0.15s ease, box-shadow 0.15s ease;
    }

    /* Hover feedback so the pills read as interactive. The bg interaction tokens are all
       near-identical dark greys, so a fill change alone is barely visible - pair it with a
       lighter inset ring (border-strong) so the hover clearly reads. */
    .copilot-topbar-pill:hover {
        background: var(--ks-bg-hover-elevated);
        box-shadow: inset 0 0 0 1px var(--ks-border-strong);
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

    /* The full-page home gives the hero + composer + help more room than the narrow dock. */
    .copilot-chat--page .copilot-empty-inner {
        max-width: 46rem;
    }

    .copilot-artwork {
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .copilot-artwork-img {
        /* 128px per the design spec; no spacing token maps to 8rem, so a raw rem is the fallback. */
        width: 8rem;
        height: 8rem;
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

    .copilot-banner {
        padding: 0 var(--ks-spacing-5);
    }

    /* Notice text + its retry action share a row; the button is pinned to the right. */
    .copilot-notice-body {
        display: flex;
        width: 100%;
        align-items: center;
        gap: var(--ks-spacing-3);
    }

    .copilot-notice-body > span {
        flex: 1;
    }

    .copilot-notice-body > :last-child {
        flex-shrink: 0;
        margin-left: auto;
    }

    .copilot-footer {
        padding: var(--ks-spacing-3) var(--ks-spacing-5);
        border-top: 1px solid var(--ks-border-subtle);
    }
</style>
