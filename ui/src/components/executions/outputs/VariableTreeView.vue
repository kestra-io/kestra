<template>
    <div class="json-tree">
        <div
            v-for="(row, index) in rows"
            :key="row.path"
            class="json-tree__row"
            :class="{'json-tree__row--selected': row.path === selectedPath}"
            :style="{'--depth': row.depth}"
            @click="$emit('select', row.path)"
        >
            <span class="json-tree__gutter">{{ index + 1 }}</span>

            <span class="json-tree__content">
                <button
                    v-if="row.isExpandable"
                    type="button"
                    class="json-tree__caret"
                    :aria-label="row.isExpanded ? $t('collapse') : $t('expand')"
                    @click.stop="toggle(row.path)"
                >
                    <ChevronDown v-if="row.isExpanded" :size="14" />
                    <ChevronRight v-else :size="14" />
                </button>
                <span v-else class="json-tree__caret-spacer" />

                <span class="json-tree__key">"{{ row.label }}"</span>
                <span class="json-tree__colon">:</span>

                <span v-if="!row.isExpandable" class="json-tree__value" :class="`json-tree__value--${row.type}`">
                    {{ row.display }}
                </span>
                <span v-else-if="!row.isExpanded" class="json-tree__preview">
                    {{ row.display }}
                </span>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    const {t} = useI18n({useScope: "global"})

    const props = defineProps<{
        value: unknown;
        basePath?: string;
        selectedPath?: string;
    }>()

    defineEmits<{
        (e: "select", path: string): void;
    }>()

    interface TreeRow {
        path: string;
        depth: number;
        label: string;
        type: string;
        display: string;
        isExpandable: boolean;
        isExpanded: boolean;
    }

    function isValidVariable(key: string): boolean {
        return /^[a-zA-Z][a-zA-Z0-9_]*$/.test(key)
    }

    function formatStep(key: string): string {
        return isValidVariable(key) ? `.${key}` : `["${key}"]`
    }

    function valueType(value: unknown): string {
        if (value === null) return "null"
        if (Array.isArray(value)) return "array"
        return typeof value
    }

    function isExpandable(value: unknown): boolean {
        if (value === null || typeof value !== "object") return false
        return Array.isArray(value)
            ? value.length > 0
            : Object.keys(value as object).length > 0
    }

    function leafDisplay(value: unknown): string {
        if (value === null) return "null"
        if (typeof value === "string") return `"${value}"`
        return String(value)
    }

    function collapsedPreview(value: unknown): string {
        if (Array.isArray(value)) {
            return value.length === 1
                ? t("variable_explorer.one_item")
                : t("variable_explorer.n_items", {count: value.length})
        }
        const count = Object.keys(value as object).length
        return count === 1
            ? t("variable_explorer.one_key")
            : t("variable_explorer.n_keys", {count})
    }

    // Paths the user has manually collapsed; everything else defaults to open.
    const collapsed = ref<Set<string>>(new Set())

    // Reset the collapse state whenever a brand new value is explored.
    watch(
        () => props.value,
        () => {
            collapsed.value = new Set()
        },
    )

    function toggle(path: string) {
        const next = new Set(collapsed.value)
        if (next.has(path)) {
            next.delete(path)
        } else {
            next.add(path)
        }
        collapsed.value = next
    }

    function buildRows(value: unknown, path: string, depth: number, rows: TreeRow[]) {
        const entries: [string, unknown][] = Array.isArray(value)
            ? value.map((item, index) => [String(index), item])
            : Object.entries(value as Record<string, unknown>)

        for (const [key, child] of entries) {
            const childPath = `${path}${formatStep(key)}`
            const expandable = isExpandable(child)
            const expanded = expandable && !collapsed.value.has(childPath)

            rows.push({
                path: childPath,
                depth,
                label: key,
                type: valueType(child),
                display: expandable ? collapsedPreview(child) : leafDisplay(child),
                isExpandable: expandable,
                isExpanded: expanded,
            })

            if (expanded) {
                buildRows(child, childPath, depth + 1, rows)
            }
        }
    }

    const rows = computed<TreeRow[]>(() => {
        const value = props.value
        if (value === null || typeof value !== "object") {
            return []
        }
        const result: TreeRow[] = []
        buildRows(value, props.basePath ?? "", 0, result)
        return result
    })
</script>

<style scoped lang="scss">
.json-tree {
    font-family: var(--ks-font-family-mono);
    font-size: var(--ks-font-size-sm);
    line-height: 1.6;
    padding: var(--ks-spacing-2) 0;

    &__row {
        display: flex;
        align-items: flex-start;
        cursor: pointer;

        &:hover {
            background-color: var(--ks-border-default);
        }

        &--selected {
            background-color: var(--ks-border-default);
        }
    }

    &__gutter {
        flex: 0 0 auto;
        width: 2.5rem;
        padding-right: var(--ks-spacing-3);
        text-align: right;
        color: var(--ks-text-secondary);
        user-select: none;
    }

    &__content {
        display: flex;
        align-items: center;
        min-width: 0;
        padding-left: calc(var(--depth) * var(--ks-spacing-4));
    }

    &__caret {
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
    }

    &__caret-spacer {
        display: inline-block;
        width: 1rem;
    }

    &__key {
        color: var(--ks-text-primary);
    }

    &__colon {
        color: var(--ks-text-secondary);
        margin-right: var(--ks-spacing-2);
    }

    &__preview {
        color: var(--ks-text-secondary);
    }

    &__value {
        word-break: break-word;

        &--string {
            color: var(--ks-text-success);
        }

        &--number,
        &--boolean {
            color: var(--ks-text-info);
        }

        &--null {
            color: var(--ks-text-secondary);
            font-style: italic;
        }
    }
}
</style>
