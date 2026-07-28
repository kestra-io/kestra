<template>
    <div v-if="displayMode === 'rows'" class="json-tree-rows">
        <div
            v-for="(row, index) in rows"
            :key="row.path"
            class="json-tree-row"
            :class="{
                'json-tree-row-selected': row.path === selectedPath,
                'json-tree-row-selectable': selectable,
            }"
            :style="{'--depth': row.depth}"
            @click="selectRow(row.path, row.value)"
        >
            <span v-if="showGutter" class="json-tree-gutter">{{ index + 1 }}</span>

            <span class="json-tree-row-content">
                <button
                    v-if="row.isExpandable"
                    type="button"
                    class="json-tree-caret"
                    :aria-expanded="row.isExpanded"
                    :aria-label="row.isExpanded ? t('ksJsonTree.collapse') : t('ksJsonTree.expand')"
                    @click.stop="toggle(row.path)"
                >
                    <KsIcon size="s" class="chevron" :class="{collapsed: !row.isExpanded}"><ChevronDown /></KsIcon>
                </button>
                <span v-else class="json-tree-caret-spacer" />

                <span class="json-tree-row-key">"{{ row.label }}"</span>
                <span class="punct">:</span>

                <span
                    v-if="!row.isExpandable"
                    class="value"
                    :class="row.valueClass"
                >
                    {{ row.display }}
                </span>
                <span v-else-if="!row.isExpanded" class="json-tree-row-preview">
                    {{ row.display }}
                </span>
            </span>
        </div>
    </div>

    <div v-else class="json-node">
        <template v-if="isBranch">
            <button
                ref="toggleEl"
                type="button"
                class="toggle"
                :aria-expanded="expanded"
                :aria-label="expanded ? t('ksJsonTree.collapse') : t('ksJsonTree.expand')"
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
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    type PreviewFormatter = (value: unknown, context: {kind: "array" | "object", count: number}) => string

    interface TreeRow {
        path: string;
        depth: number;
        label: string;
        valueClass: string;
        value: unknown;
        display: string;
        isExpandable: boolean;
        isExpanded: boolean;
    }

    const props = withDefaults(defineProps<{
        value: unknown,
        nodeKey?: string | number,
        depth?: number,
        defaultExpanded?: boolean,
        displayMode?: "inline" | "rows",
        showGutter?: boolean,
        selectable?: boolean,
        basePath?: string,
        selectedPath?: string,
        previewFormatter?: PreviewFormatter,
    }>(), {depth: 0})

    const emit = defineEmits<{
        select: [path: string, value: any],
    }>()

    const {t} = useI18n()

    const expanded = ref(props.defaultExpanded ?? props.depth < 1)
    const collapsed = ref<Set<string>>(new Set())

    watch(() => props.defaultExpanded, (value) => {
        if (value !== undefined) expanded.value = value
    })

    watch(
        () => props.value,
        () => {
            collapsed.value = new Set()
        },
    )

    const displayMode = computed(() => (props.depth === 0 ? props.displayMode : "inline"))
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

    function isValidVariable(key: string): boolean {
        return /^[a-zA-Z][a-zA-Z0-9_]*$/.test(key)
    }

    function formatStep(key: string): string {
        return isValidVariable(key) ? `.${key}` : `["${key}"]`
    }

    function isExpandable(value: unknown): boolean {
        if (value === null || typeof value !== "object") return false
        return Array.isArray(value)
            ? value.length > 0
            : Object.keys(value as object).length > 0
    }

    function shorten(value: unknown): string {
        if (value === null) return "null"
        if (Array.isArray(value)) return "[…]"
        if (typeof value === "object") return "{…}"
        if (typeof value === "string") return `"${value.length > 24 ? value.slice(0, 24) + "…" : value}"`
        return String(value)
    }

    function display(value: unknown): string {
        if (value === null) return "null"
        if (typeof value === "string") return `"${value}"`
        return String(value)
    }

    function tokenClass(value: unknown): string {
        if (typeof value === "string") return "is-string"
        if (typeof value === "number") return "is-number"
        if (value !== null && typeof value === "object") return "is-branch"
        return "is-literal"
    }

    function collapsedPreview(value: unknown): string {
        const kind = Array.isArray(value) ? "array" : "object"
        const count = Array.isArray(value)
            ? value.length
            : Object.keys(value as object).length

        return props.previewFormatter?.(value, {kind, count}) ?? (kind === "array" ? "[…]" : "{…}")
    }

    function toggle(path: string) {
        const next = new Set(collapsed.value)
        if (next.has(path)) {
            next.delete(path)
        } else {
            next.add(path)
        }
        collapsed.value = next
    }

    function selectRow(path: string, value: unknown) {
        if (props.selectable) {
            emit("select", path, value)
        }
    }

    function buildRows(value: unknown, path: string, depth: number, result: TreeRow[]) {
        const rowEntries: [string, unknown][] = Array.isArray(value)
            ? value.map((item, index) => [String(index), item])
            : Object.entries(value as Record<string, unknown>)

        for (const [key, child] of rowEntries) {
            const childPath = `${path}${formatStep(key)}`
            const expandable = isExpandable(child)
            const rowExpanded = expandable && props.defaultExpanded !== false && !collapsed.value.has(childPath)

            result.push({
                path: childPath,
                depth,
                value: child,
                label: key,
                valueClass: tokenClass(child),
                display: expandable ? collapsedPreview(child) : display(child),
                isExpandable: expandable,
                isExpanded: rowExpanded,
            })

            if (rowExpanded) {
                buildRows(child, childPath, depth + 1, result)
            }
        }
    }

    const rows = computed<TreeRow[]>(() => {
        if (!isBranch.value) {
            return []
        }

        const result: TreeRow[] = []
        buildRows(props.value, props.basePath ?? "", 0, result)
        return result
    })

    const toggleEl = ref<HTMLElement>()
    const availableChars = ref(48)
    let resizeObserver: ResizeObserver | undefined

    onMounted(() => {
        const container = toggleEl.value?.parentElement
        if (props.depth !== 0 || !container || typeof ResizeObserver === "undefined") {
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
        return display(props.value)
    })
</script>

<style scoped lang="scss">
    .json-tree-rows {
        font-family: var(--ks-font-family-mono);
        font-size: var(--ks-font-size-sm);
        line-height: 1.6;
        padding: var(--ks-spacing-2) 0;
        overflow: auto;
    }

    .json-tree-row {
        display: flex;
        align-items: flex-start;

        &:hover {
            background-color: var(--ks-bg-hover);
        }
    }

    .json-tree-row-selectable {
        cursor: pointer;
    }

    .json-tree-row-selected {
        background-color: var(--ks-bg-hover);
    }

    .json-tree-gutter {
        flex: 0 0 auto;
        width: 2.5rem;
        padding-right: var(--ks-spacing-3);
        text-align: right;
        color: var(--ks-text-secondary);
        user-select: none;
    }

    .json-tree-row-content {
        display: flex;
        align-items: center;
        min-width: 0;
        gap: var(--ks-spacing-1);
        padding-left: calc(var(--depth) * var(--ks-spacing-4));
    }

    .json-tree-caret {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1rem;
        height: 1rem;
        padding: 0;
        border: none;
        background: transparent;
        cursor: pointer;
        color: var(--ks-text-secondary);
        font: inherit;
    }

    .json-tree-caret-spacer {
        display: inline-block;
        width: 1rem;
        flex: 0 0 auto;
    }

    .json-tree-row-key {
        color: var(--ks-text-primary);
    }

    .json-tree-row-preview {
        color: var(--ks-text-secondary);
    }

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
