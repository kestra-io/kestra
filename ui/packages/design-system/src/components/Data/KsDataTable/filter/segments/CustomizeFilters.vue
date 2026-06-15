<template>
    <div class="filters-panel">
        <div class="header">
            <div class="title">
                <h6>{{ $t("filter.customize") }}</h6>
                <small>{{ $t("filter.select filter") }}</small>
            </div>
            <KsButton
                link
                :icon="Close"
                @click="$emit('close')"
                size="small"
                class="close-icon"
            />
        </div>

        <div class="list">
            <div
                v-for="key in configuration.keys"
                :key="key.key"
                class="item"
                :draggable="true"
                @click="addFilterForKey(key)"
                @dragstart="(e) => onFieldDragStart(e, key.key)"
                @dragend="$emit('drag-end-field')"
            >
                <div class="info">
                    <span class="label">{{ key.label }}</span>
                    <small>{{ key.description }}</small>
                </div>

                <KsButton
                    link
                    size="default"
                    :icon="Plus"
                    class="unselected"
                    @click.stop="addFilterForKey(key)"
                />
            </div>
        </div>

        <div class="footer">
            <small>{{ $t("filter.filters_added", {selected: selectedCount, total: totalCount}) }}</small>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Close, Plus} from "../utils/icons"
    import {
        COMPARATOR_LABELS,
        type FilterConfiguration,
        type FilterKeyConfig,
        type AppliedFilter,
    } from "../utils/filterTypes"

    const props = defineProps<{
        configuration: FilterConfiguration;
        appliedFilters: AppliedFilter[];
    }>()

    const emits = defineEmits<{
        close: [];
        "add-filter": [filter: AppliedFilter];
        "remove-filter": [id: string];
        "drag-end-field": [];
    }>()

    const FIELD_DRAG_MIME = "application/x-kestra-filter-entity"

    /**
     * Build a transient DOM node styled to look like an empty FilterChip so the browser's
     * drag preview matches what's actually being created on drop. The node lives off-screen
     * for one frame so the browser can snapshot it, then we tear it down on the next tick.
     */
    const buildDragPreview = (label: string): HTMLElement => {
        const preview = document.createElement("div")
        preview.style.cssText = [
            "display: inline-flex",
            "align-items: center",
            "gap: 6px",
            "background-color: var(--ks-btn-secondary-bg-default)",
            "border: 1px solid var(--ks-border-default)",
            "padding: 3px 12px",
            "border-radius: 4px",
            "min-height: 32px",
            "max-height: 32px",
            "font-family: var(--ks-font-family-sans, inherit)",
            "font-size: var(--ks-font-size-xs)",
            "color: var(--ks-text-primary)",
            "box-shadow: 0 1px 2px var(--ks-shadow-surface)",
            "position: absolute",
            "top: -1000px",
            "left: -1000px",
            "pointer-events: none",
            "white-space: nowrap",
        ].join("; ")
        const keySpan = document.createElement("span")
        keySpan.textContent = label
        preview.append(keySpan)
        document.body.appendChild(preview)
        return preview
    }

    const onFieldDragStart = (event: DragEvent, keyName: string) => {
        if (!event.dataTransfer) return
        event.dataTransfer.effectAllowed = "copy"
        event.dataTransfer.setData(FIELD_DRAG_MIME, `field:${keyName}`)
        event.dataTransfer.setData("text/plain", keyName)

        const keyConfig = props.configuration.keys.find((k) => k.key === keyName)
        const preview = buildDragPreview(keyConfig?.label ?? keyName)
        // Offset the cursor toward the chip's left edge so the preview sits naturally under the pointer.
        event.dataTransfer.setDragImage(preview, 12, 16)
        // Browsers snapshot the element synchronously at dragstart; remove it after the current task.
        setTimeout(() => preview.remove(), 0)
    }

    const selectedCount = computed(() =>
        new Set(props.appliedFilters.map(f => f.key)).size,
    )
    const totalCount = computed(() => props.configuration.keys.length)

    const addFilterForKey = (key: FilterKeyConfig) => {
        const comparator = key.comparators?.[0]
        if (!comparator) return
        const newFilter: AppliedFilter = {
            id: `${key.key}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
            key: key.key,
            keyLabel: key.label,
            comparator,
            comparatorLabel: COMPARATOR_LABELS[comparator],
            value: [],
            valueLabel: "",
        }
        emits("add-filter", newFilter)
    }

</script>

<style lang="scss" scoped>
.filters-panel {
    height: fit-content;
    max-height: 500px;
    display: flex;
    flex-direction: column;
    border-radius: 8px;

    small {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-dim);
        font-weight: 400;
    }

    .header {
        display: flex;
        justify-content: space-between;
        align-items: flex-start;
        padding: 0.75rem 1rem 0.5rem;
        background-color: var(--ks-bg-active);
        border-bottom: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 1;

        .title {
            h6 {
                margin: 0;
                font-size: var(--ks-font-size-sm);
                font-weight: 700;
            }
        }

        :deep(.close-icon) {
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-base);
            cursor: pointer;
            padding-right: 0;

            &:hover {
                color: var(--ks-text-link);
            }
        }
    }

    .list {
        flex: 1;
        overflow-y: auto;
        scrollbar-width: thin;
        scrollbar-color: transparent transparent;

        &:hover {
            scrollbar-color: var(--ks-border-subtle) transparent;
        }
    }

    .item {
        display: flex;
        justify-content: space-between;
        align-items: center;
        padding: 0.5rem 1rem;
        cursor: pointer;
        transition: all 0.2s ease;
        border-bottom: 1px solid var(--ks-border-default);

        &:hover {
            background-color: var(--ks-bg-hover-elevated);
        }

        &:last-child {
            border-bottom: none;
        }

        .info {
            display: flex;
            align-items: start;
            flex-direction: column;

            .label {
                font-size: var(--ks-font-size-sm);
                font-weight: 400;
                line-height: 1.375rem;
            }
        }
    }

    .footer {
        border-top: 1px solid var(--ks-border-default);
        flex-shrink: 0;
        position: sticky;
        bottom: 0;
        z-index: 1;
        padding: 0.5rem 1rem;
        text-align: center;
    }
}

:deep(.kel-button.unselected) {
    color: var(--ks-status-success);
    user-select: none;
    pointer-events: auto;
    font-size: var(--ks-font-size-lg);

    &:hover {
        color: var(--ks-text-success);
    }
}
</style>