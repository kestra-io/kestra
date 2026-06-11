<template>
    <div class="json-node">
        <template v-if="isBranch">
            <button
                ref="toggleEl"
                type="button"
                class="toggle"
                :aria-expanded="expanded"
                :aria-label="expanded ? t('collapse') : t('expand')"
                @click="expanded = !expanded"
            >
                <KsIcon size="s" class="chevron" :class="{collapsed: !expanded}"><ChevronDown /></KsIcon>
                <span v-if="nodeKey !== undefined" class="key">{{ nodeKey }}</span>
                <span class="punct">{{ open }}</span>
                <span v-if="!expanded" class="preview">
                    <template v-for="(entry, i) in previewEntries" :key="i">
                        <span v-if="!isArray" class="key">{{ entry.key }}</span><span v-if="!isArray" class="punct">: </span><span class="value" :class="entry.cls">{{ entry.display }}</span><span v-if="i < previewEntries.length - 1" class="punct">, </span>
                    </template>
                    <span v-if="previewMore" class="punct">{{ previewEntries.length ? ", " : "" }}+{{ previewMore }}</span>
                    <span class="punct">&nbsp;{{ close }}</span>
                </span>
            </button>
            <div v-if="expanded" class="children">
                <KsJsonTree
                    v-for="entry in entries"
                    :key="entry.key"
                    :value="entry.value"
                    :nodeKey="entry.key"
                    :depth="depth + 1"
                />
            </div>
            <span v-if="expanded" class="punct close">{{ close }}</span>
        </template>

        <div v-else class="leaf">
            <span v-if="nodeKey !== undefined" class="key">{{ nodeKey }}</span>
            <span v-if="nodeKey !== undefined" class="punct">:</span>
            <span :class="['value', valueClass]">{{ displayValue }}</span>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch, onMounted, onBeforeUnmount} from "vue"
    import {useI18n} from "vue-i18n"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    const props = withDefaults(defineProps<{
        value: unknown,
        nodeKey?: string | number,
        depth?: number,
        defaultExpanded?: boolean,
    }>(), {depth: 0})

    const {t} = useI18n()

    const expanded = ref(props.defaultExpanded ?? props.depth < 1)

    watch(() => props.defaultExpanded, (value) => {
        if (value !== undefined) expanded.value = value
    })

    const isArray = computed(() => Array.isArray(props.value))
    const isBranch = computed(() => props.value !== null && typeof props.value === "object")

    const entries = computed(() => {
        if (!isBranch.value) {
            return []
        }
        if (isArray.value) {
            return (props.value as unknown[]).map((value, index) => ({key: index, value}))
        }
        return Object.entries(props.value as Record<string, unknown>).map(([key, value]) => ({key, value}))
    })

    const open = computed(() => (isArray.value ? "[" : "{"))
    const close = computed(() => (isArray.value ? "]" : "}"))

    function shorten(value: unknown): string {
        if (value === null) return "null"
        if (Array.isArray(value)) return "[…]"
        if (typeof value === "object") return "{…}"
        if (typeof value === "string") return `"${value.length > 24 ? value.slice(0, 24) + "…" : value}"`
        return String(value)
    }

    function tokenClass(value: unknown): string {
        if (typeof value === "string") return "is-string"
        if (typeof value === "number") return "is-number"
        if (value !== null && typeof value === "object") return "is-branch"
        return "is-literal"
    }

    const toggleEl = ref<HTMLElement>()
    const availableChars = ref(48)
    let resizeObserver: ResizeObserver | undefined

    onMounted(() => {
        const container = toggleEl.value?.parentElement
        if (props.depth !== 0 || !container) {
            return
        }
        const charPx = (parseFloat(getComputedStyle(toggleEl.value!).fontSize) || 12) * 0.6
        resizeObserver = new ResizeObserver(([entry]) => {
            availableChars.value = Math.max(12, Math.floor(entry.contentRect.width / charPx) - 8)
        })
        resizeObserver.observe(container)
    })

    onBeforeUnmount(() => resizeObserver?.disconnect())

    const previewLimit = computed(() => {
        if (props.depth !== 0) {
            return 3
        }
        let used = 0
        let count = 0
        for (const entry of entries.value) {
            const len = (isArray.value ? 0 : String(entry.key).length + 2) + shorten(entry.value).length + 2
            if (count > 0 && used + len > availableChars.value) {
                break
            }
            used += len
            count++
        }
        return Math.max(1, count)
    })

    const previewEntries = computed(() =>
        entries.value.slice(0, previewLimit.value).map(e => ({
            key: e.key,
            display: shorten(e.value),
            cls: tokenClass(e.value),
        })),
    )

    const previewMore = computed(() => Math.max(0, entries.value.length - previewLimit.value))

    const valueClass = computed(() => {
        const v = props.value
        if (typeof v === "string") return "is-string"
        if (typeof v === "number") return "is-number"
        return "is-literal"
    })

    const displayValue = computed(() => {
        const v = props.value
        if (v === null) return "null"
        if (typeof v === "string") return `"${v}"`
        return String(v)
    })
</script>

<style scoped lang="scss">
    .json-node {
        font-family: var(--ks-font-family-mono);
        line-height: 1.7;
    }

    .toggle {
        background: none;
        border: none;
        padding: 0;
        cursor: pointer;
        color: inherit;
        font: inherit;
    }

    .toggle {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        line-height: 1;
        max-width: 100%;
        overflow: hidden;
        white-space: nowrap;
        border-radius: var(--ks-radius-xs);

        &:hover {
            color: var(--ks-text-primary);
        }

        :deep(.material-design-icon) {
            display: inline-flex;
            align-items: center;
            line-height: 0;
        }
    }

    .chevron {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.3em;
        height: 1.3em;
        transition: transform 0.15s ease;

        :deep(svg) {
            width: 100%;
            height: 100%;
            display: block;
        }

        &.collapsed {
            transform: rotate(-90deg);
        }
    }

    .children {
        padding-left: var(--ks-spacing-3);
        border-left: 1px solid var(--ks-border-subtle);
        margin-left: var(--ks-spacing-2);
    }

    .leaf {
        display: inline-flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        border-radius: var(--ks-radius-xs);
        padding-inline: var(--ks-spacing-1);
        margin-inline: calc(-1 * var(--ks-spacing-1));

        &:hover {
            background: var(--ks-bg-hover);
        }
    }

    .key {
        color: var(--ks-editor-property);
    }

    .punct {
        color: var(--ks-editor-punctuation);
    }

    .preview {
        opacity: 0.85;
    }

    .value {
        &.is-string {
            color: var(--ks-editor-value);
        }

        &.is-number, &.is-literal {
            color: var(--ks-editor-pabble);
        }

        &.is-branch {
            color: var(--ks-editor-punctuation);
        }
    }
</style>
