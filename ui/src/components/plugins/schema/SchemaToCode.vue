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
    import {computed, ref, watchEffect} from "vue"
    import {KsButton, KsTooltip, copyToClipboard as writeToClipboard} from "@kestra-io/design-system"
    import {loadLanguageOnDemand, type HighlighterCore} from "@kestra-io/design-system/shiki"
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

    const resolvedLanguage = ref("text")

    watchEffect(async () => {
        const {highlighter} = props
        const requested = props.language ?? "text"

        if (requested === "text" || highlighter.getLoadedLanguages().includes(requested)) {
            resolvedLanguage.value = requested
            return
        }

        // Plugin authors pick example.lang freely, so it may be a grammar the
        // shared highlighter does not pre-register; render it plain until (and
        // unless) that grammar loads, rather than letting codeToHtml throw.
        resolvedLanguage.value = "text"
        if (await loadLanguageOnDemand(highlighter, requested) && props.language === requested) {
            resolvedLanguage.value = requested
        }
    })

    const codeData = computed(() => props.highlighter.codeToHtml(props.code, {
        lang: resolvedLanguage.value,
        theme: props.theme,
    }))

    function copyToClipboard() {
        clearTimeout(copyResetTimer.value)
        writeToClipboard(props.code.trimEnd())
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
        background-color: var(--ks-bg-input);
        border: 1px solid var(--ks-border-default);
        border-radius: 0.5rem;

        .language {
            font-size: var(--ks-font-size-xs);
            color: var(--ks-text-dim);
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
            color: var(--ks-text-primary);

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
