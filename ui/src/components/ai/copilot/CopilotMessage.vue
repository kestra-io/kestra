<template>
    <!-- User prompt -->
    <div v-if="message.role === 'USER'" class="copilot-msg copilot-msg-user">
        <div class="copilot-bubble copilot-bubble-user">
            <KsText size="small" class="copilot-bubble-text">{{ message.content }}</KsText>
        </div>
    </div>

    <!-- Assistant streamed text -->
    <div v-else-if="message.type === 'TEXT'" class="copilot-msg copilot-msg-assistant">
        <div class="copilot-bubble copilot-bubble-assistant">
            <KsMarkdown v-if="message.content" :content="message.content" />
        </div>
    </div>

    <!-- Tool call / tool result — collapsible technical detail -->
    <div v-else-if="message.type === 'TOOL_CALL'" class="copilot-msg copilot-tool">
        <KsCollapse v-model="expanded">
            <KsCollapseItem name="tool">
                <!-- Title via slot so it renders at the same small/secondary treatment as the
                     rest of the transcript (the default collapse header is larger + primary). -->
                <template #title>
                    <KsText size="small" class="copilot-tool-label">
                        {{ t("ai.copilot.toolCall", {tool: message.toolCall?.tool ?? ""}) }}
                    </KsText>
                </template>
                <pre class="copilot-tool-args">{{ argsJson }}</pre>
            </KsCollapseItem>
        </KsCollapse>
    </div>

    <div v-else-if="message.type === 'TOOL_RESULT'" class="copilot-msg copilot-tool-result">
        <KsIcon class="copilot-tool-result-icon" :class="isOk ? 'is-ok' : 'is-error'">
            <CheckCircleOutline v-if="isOk" />
            <CloseCircleOutline v-else />
        </KsIcon>
        <KsText size="small" class="copilot-tool-label">
            {{ isOk ? t("ai.copilot.toolResult.ok", {tool: toolName}) : t("ai.copilot.toolResult.rejected", {tool: toolName}) }}
        </KsText>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed} from "vue"
    import {useI18n} from "vue-i18n"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import CloseCircleOutline from "vue-material-design-icons/CloseCircleOutline.vue"
    import type {ChatMessage} from "./useAiChat"

    const props = defineProps<{message: ChatMessage}>()

    const {t} = useI18n()

    const expanded = ref<string[]>([])

    const argsJson = computed(() => JSON.stringify(props.message.toolCall?.arguments ?? {}, null, 2))
    const toolName = computed(() => props.message.toolResult?.tool ?? "")
    const isOk = computed(() => props.message.toolResult?.outcome === "ok")
</script>

<style scoped>
    .copilot-msg {
        margin-bottom: var(--ks-spacing-3);
    }

    .copilot-msg-user {
        display: flex;
        justify-content: flex-end;
    }

    .copilot-msg-assistant {
        display: flex;
        justify-content: flex-start;
    }

    .copilot-bubble-user {
        max-width: 85%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-elevated);
    }

    /* Assistant replies get their own left-aligned bubble (surface fill) so they read as
       styled responses rather than plain text. */
    .copilot-bubble-assistant {
        max-width: 90%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-subtle);
        /* Copilot body copy sits at sm (12px), matching user bubbles / tool strip / plan card.
           .ks-markdown sets no font-size of its own, so this cascades into its paragraphs,
           list items and inline code — keeping every message the same base size. */
        font-size: var(--ks-font-size-sm);
        line-height: 1.5;
    }

    .copilot-tool-args {
        margin: 0;
        font-family: monospace;
        white-space: pre-wrap;
        word-break: break-word;
        color: var(--ks-text-secondary);
    }

    /* Body copy (user bubble) and the tool strip sit at sm (12px), uniform with the rest. */
    .copilot-bubble-text,
    .copilot-tool-label {
        font-size: var(--ks-font-size-sm);
    }

    /* Tool call header + tool result share one subdued treatment (secondary),
       so the whole "tool activity" strip reads uniformly. Status is carried by the icon. */
    .copilot-tool-label {
        --kel-text-color: var(--ks-text-secondary);
    }

    .copilot-tool-result {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .copilot-tool-result-icon.is-ok {
        color: var(--ks-text-success);
    }

    .copilot-tool-result-icon.is-error {
        color: var(--ks-text-error);
    }
</style>
