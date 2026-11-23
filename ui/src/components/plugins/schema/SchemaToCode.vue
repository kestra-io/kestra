<template>
    <div class="code-block" :class="{dense}" @mouseover="isHoveringCode = true" @mouseleave="isHoveringCode = false">
        <div v-if="language && !isHoveringCode" class="language">
            {{ language }}
        </div>
        <KsTooltip
            v-if="isHoveringCode"
            :visible="copied"
            :content="$t('copied')"
            placement="left"
            trigger="manual"
        >
            <KsButton
                class="copy"
                :aria-label="$t('copy')"
                :icon="copied ? Check : ContentCopy"
                link
                @click="copyToClipboard"
            />
        </KsTooltip>
        <div>
            <component :is="renderCode()" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {Fragment, computed, h, ref, watchEffect, type VNode} from "vue"
    import {KsButton, KsTooltip, copyToClipboard as writeToClipboard} from "@kestra-io/design-system"
    import {isSpecialLang, loadLanguageOnDemand, type HighlighterCore} from "@kestra-io/design-system/shiki"
    import {pebbleTransformer} from "@kestra-io/plumage"
    import Check from "vue-material-design-icons/Check.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"

    const COPY_RESET_DELAY_MS = 2000

    type HastNode = ReturnType<HighlighterCore["codeToHast"]>["children"][number]

    const props = withDefaults(defineProps<{
        highlighter: HighlighterCore;
        code?: string;
        language?: string | null;
        theme?: string;
        /** Sets the code in the smallest text size, for an example nested in a definition. */
        dense?: boolean;
    }>(), {
        code: "",
        language: null,
        theme: "kestra-dark",
        dense: false,
    })

    const isHoveringCode = ref(false)
    const copied = ref(false)
    const copyResetTimer = ref<ReturnType<typeof setTimeout>>()

    const resolvedLanguage = ref("text")

    watchEffect(async () => {
        const {highlighter} = props
        const requested = props.language ?? "text"

        if (isSpecialLang(requested) || highlighter.getLoadedLanguages().includes(requested)) {
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

    const hast = computed(() => props.highlighter.codeToHast(props.code, {
        lang: resolvedLanguage.value,
        theme: props.theme,
        transformers: [pebbleTransformer()],
    }))

    function toVNodes(nodes: HastNode[]): (VNode | string)[] {
        return nodes.flatMap((node): (VNode | string)[] => {
            if (node.type === "text") {
                return [node.value]
            }

            return node.type === "element" ? [h(node.tagName, node.properties, toVNodes(node.children))] : []
        })
    }

    function renderCode(): VNode {
        return h(Fragment, toVNodes(hast.value.children))
    }

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

<style scoped>
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

        & pre {
            margin-bottom: 0;
            padding: 0;
            border: 0 !important;
        }

        & .shiki {
            background-color: transparent !important;

            code {
                display: flex;
                flex-direction: column;
            }
        }

        .copy {
            color: var(--ks-text-primary);
            --ks-icon-box-size: 1.125rem;
            --ks-icon-glyph-size: 1.125rem;
        }

        .copy, .language {
            position: absolute;
            top: 0.75rem;
            right: 0.75rem;
        }
    }

    pre code .line {
        display: block;
        min-height: 1rem;
        white-space: pre-wrap;
    }

    .dense pre code .line {
        font-size: var(--ks-font-size-xs);
    }
</style>
