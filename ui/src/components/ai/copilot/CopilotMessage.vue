<template>
    <!-- User prompt -->
    <div v-if="message.role === 'USER'" class="copilot-msg copilot-msg-user">
        <div class="copilot-bubble copilot-bubble-user">
            <KsText size="small">{{ message.content }}</KsText>
        </div>
    </div>

    <!-- Assistant streamed text -->
    <div v-else-if="message.type === 'TEXT'" class="copilot-msg copilot-msg-assistant">
        <KsMarkdown v-if="message.content" :content="message.content" />
    </div>

    <!-- Tool call / tool result — collapsible technical detail -->
    <div v-else-if="message.type === 'TOOL_CALL'" class="copilot-msg copilot-tool">
        <KsCollapse v-model="expanded">
            <KsCollapseItem name="tool" :title="t('ai.copilot.toolCall', {tool: message.toolCall?.tool ?? ''})">
                <pre class="copilot-tool-args">{{ argsJson }}</pre>
            </KsCollapseItem>
        </KsCollapse>
    </div>

    <div v-else-if="message.type === 'TOOL_RESULT'" class="copilot-msg copilot-tool-result">
        <KsIcon>
            <CheckCircleOutline v-if="isOk" />
            <CloseCircleOutline v-else />
        </KsIcon>
        <KsText size="small" :type="isOk ? 'success' : 'danger'">
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

    .copilot-bubble-user {
        max-width: 85%;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-elevated);
    }

    .copilot-tool-args {
        margin: 0;
        font-family: monospace;
        white-space: pre-wrap;
        word-break: break-word;
        color: var(--ks-text-secondary);
    }

    .copilot-tool-result {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }
</style>
