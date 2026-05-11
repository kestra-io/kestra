<template>
    <div class="code-block" @mouseover="isHoveringCode = true" @mouseleave="isHoveringCode = false">
        <div v-if="language && !isHoveringCode" class="language">
            {{ language }}
        </div>
        <KsTooltip
            v-if="isHoveringCode"
            :visible="copied"
            content="Copied!"
            placement="left"
            trigger="manual"
        >
            <KsButton
                class="copy"
                :icon="copied ? Check : ContentCopy"
                link
                @click="copyToClipboard"
            />
        </KsTooltip>
        <div v-html="codeData" />
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import type {HighlighterCore} from "shiki/core"
    import {KsButton, KsTooltip} from "@kestra-io/design-system"
    import Check from "vue-material-design-icons/Check.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    const COPY_RESET_DELAY_MS = 2000

    const props = withDefaults(defineProps<{
        highlighter: HighlighterCore;
        code?: string;
        language?: string | null;
        theme?: string;
    }>(), {
        code: "",
        language: null,
        theme: "github-dark",
    })

    const isHoveringCode = ref(false)
    const copied = ref(false)
    const copyResetTimer = ref<ReturnType<typeof setTimeout>>()

    const codeData = computed(() => props.highlighter.codeToHtml(props.code, {
        lang: props.language ?? "text",
        theme: props.theme,
    }))

    function copyToClipboard() {
        clearTimeout(copyResetTimer.value)
        navigator.clipboard.writeText(props.code.trimEnd())
        copied.value = true

        copyResetTimer.value = setTimeout(() => {
            copied.value = false
            copyResetTimer.value = undefined
        }, COPY_RESET_DELAY_MS)
    }
</script>

<style lang="scss" scoped>
    .code-block {
        position: relative;
        padding: 0.75rem;
        background-color: var(--ks-background-input);
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;

        .language {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-content-tertiary);
        }

        :deep(pre) {
            margin-bottom: 0;
            padding: 0;
            border: 0 !important;
        }

        :deep(.shiki) {
            background-color: transparent !important;

            code {
                display: flex;
                flex-direction: column;
            }
        }

        .copy {
            color: var(--ks-content-primary);

            :deep(.material-design-icon) {
                &, & * {
                    height: 1.125rem !important;
                    width: 1.125rem !important;
                }
            }
        }

        .copy, .language {
            position: absolute;
            top: 0.75rem;
            right: 0.75rem;
        }
    }

    :deep(pre code .line) {
        display: block;
        min-height: 1rem;
        white-space: pre-wrap;
    }
</style>
