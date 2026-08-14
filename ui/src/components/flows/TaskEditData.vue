<template>
    <button
        v-if="isCollapsed"
        class="task-edit-data-rail"
        type="button"
        :aria-label="`${t('expand')} — ${title}`"
        :title="title"
        :data-test="`task-edit-data-${kind}`"
        @click="emit('toggle')"
    >
        <component :is="stacked ? ChevronUp : (side === 'right' ? ChevronLeft : ChevronRight)" class="task-edit-data-rail-ico" />
        <span class="task-edit-data-rail-label">{{ title }}</span>
    </button>
    <div v-else class="task-edit-data" :data-test="`task-edit-data-${kind}`">
        <div class="task-edit-data-head">
            <div class="task-edit-data-head-text">
                <span class="task-edit-data-title">{{ title }}</span>
                <span class="task-edit-data-sub">{{ subtitle }}</span>
            </div>
            <KsIconButton
                v-if="collapsible"
                class="task-edit-data-collapse"
                :tooltip="t('collapse')"
                @click="emit('toggle')"
            >
                <component :is="stacked ? ChevronDown : (side === 'right' ? ChevronRight : ChevronLeft)" />
            </KsIconButton>
        </div>

        <div v-if="filterable" class="task-edit-data-filter">
            <Magnify class="task-edit-data-filter-ico" />
            <input
                v-model="filter"
                class="task-edit-data-filter-input"
                :placeholder="t('block_editor.filter_data')"
                :aria-label="t('block_editor.filter_data')"
                autocomplete="off"
                spellcheck="false"
            >
        </div>

        <div class="task-edit-data-scroll">
            <div v-for="section in visibleSections" :key="section.key" class="task-edit-data-section">
                <button
                    class="task-edit-data-section-head"
                    type="button"
                    :aria-expanded="!collapsed.has(section.key)"
                    @click="toggle(section.key)"
                >
                    <ChevronRight class="task-edit-data-chevron" :class="{'task-edit-data-chevron--open': !collapsed.has(section.key)}" />
                    <span class="task-edit-data-section-label">{{ section.label }}</span>
                    <span class="task-edit-data-count">{{ section.chips.length }}</span>
                </button>

                <div v-if="!collapsed.has(section.key)" class="task-edit-data-chips">
                    <template v-for="chip in section.chips" :key="chip.label">
                        <button
                            v-if="interactive"
                            class="task-edit-data-chip"
                            type="button"
                            draggable="true"
                            :title="chip.expr"
                            @click="chip.expr && copy(chip.expr)"
                            @dragstart="chip.expr && onDragStart($event, chip.expr)"
                        >
                            <span class="task-edit-data-chip-label">{{ chip.label }}</span>
                            <span v-if="copied === chip.expr" class="task-edit-data-chip-action">{{ t("copied") }}</span>
                        </button>
                        <div v-else class="task-edit-data-chip task-edit-data-chip--static">
                            <span class="task-edit-data-chip-label">{{ chip.label }}</span>
                            <span v-if="chip.type" class="task-edit-data-chip-type">{{ chip.type }}</span>
                        </div>
                    </template>
                </div>
            </div>

            <p v-if="visibleSections.length === 0" class="task-edit-data-empty">
                {{ t("block_editor.no_data_matches") }}
            </p>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useI18n} from "vue-i18n"
    import {copyToClipboard} from "@kestra-io/design-system"
    import Magnify from "vue-material-design-icons/Magnify.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    interface DataChip {
        label: string
        expr?: string
        type?: string
    }
    interface DataSection {
        key: string
        label: string
        chips: DataChip[]
    }

    const props = withDefaults(defineProps<{
        kind: string
        title: string
        subtitle: string
        sections: DataSection[]
        filterable?: boolean
        collapsible?: boolean
        isCollapsed?: boolean
        side?: "left" | "right"
        stacked?: boolean
        interactive?: boolean
    }>(), {
        filterable: false,
        collapsible: false,
        isCollapsed: false,
        side: "left",
        stacked: false,
        interactive: true,
    })

    const emit = defineEmits<{(e: "toggle"): void}>()

    const {t} = useI18n()

    const filter = ref("")
    const collapsed = ref(new Set<string>())
    const copied = ref<string | undefined>(undefined)

    const visibleSections = computed<DataSection[]>(() => {
        const q = filter.value.trim().toLowerCase()
        return props.sections
            .map(section => q
                ? {...section, chips: section.chips.filter(c => c.label.toLowerCase().includes(q) || (c.expr ?? "").toLowerCase().includes(q))}
                : section)
            .filter(section => section.chips.length > 0)
    })

    function toggle(key: string) {
        if (collapsed.value.has(key)) collapsed.value.delete(key)
        else collapsed.value.add(key)
        collapsed.value = new Set(collapsed.value)
    }

    function onDragStart(event: DragEvent, expr: string) {
        event.dataTransfer?.setData("text/plain", expr)
        if (event.dataTransfer) event.dataTransfer.effectAllowed = "copy"
    }

    let copiedTimer: ReturnType<typeof setTimeout> | undefined
    function copy(expr: string) {
        copyToClipboard(expr)
        copied.value = expr
        clearTimeout(copiedTimer)
        copiedTimer = setTimeout(() => {
            copied.value = undefined
        }, 1200)
    }
</script>

<style scoped lang="scss">
    .task-edit-data {
        display: flex;
        flex-direction: column;
        min-height: 0;
        background: var(--ks-bg-base);
    }

    .task-edit-data-head {
        display: flex;
        align-items: flex-start;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3) var(--ks-spacing-2) var(--ks-spacing-2) var(--ks-spacing-3);
    }

    .task-edit-data-head-text {
        display: flex;
        flex-direction: column;
        gap: 1px;
        min-width: 0;
    }

    .task-edit-data-collapse {
        flex-shrink: 0;
    }

    .task-edit-data-rail {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        height: 100%;
        padding: var(--ks-spacing-3) 0;
        background: var(--ks-bg-base);
        border: none;
        cursor: pointer;
        color: var(--ks-text-secondary);
        transition: color 0.12s, background-color 0.12s;
    }

    .task-edit-data-rail:hover {
        color: var(--ks-text-primary);
        background: var(--ks-bg-hover);
    }

    .task-edit-data-rail-ico {
        display: flex;
        font-size: var(--ks-font-size-sm);
    }

    .task-edit-data-rail-label {
        writing-mode: vertical-rl;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
    }

    @container (max-width: 760px) {
        .task-edit-data-rail {
            flex-direction: row;
            justify-content: space-between;
            height: auto;
            padding: var(--ks-spacing-2) var(--ks-spacing-3);
        }

        .task-edit-data-rail-ico {
            order: 1;
        }

        .task-edit-data-rail-label {
            writing-mode: horizontal-tb;
        }
    }

    .task-edit-data-title {
        font-size: var(--ks-font-size-sm);
        font-weight: 600;
        color: var(--ks-text-primary);
    }

    .task-edit-data-sub {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
    }

    .task-edit-data-filter {
        position: relative;
        display: flex;
        align-items: center;
        margin: 0 var(--ks-spacing-3) var(--ks-spacing-2);
    }

    .task-edit-data-filter-ico {
        position: absolute;
        left: var(--ks-spacing-2);
        display: flex;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-muted);
        pointer-events: none;
    }

    .task-edit-data-filter-input {
        width: 100%;
        padding: var(--ks-spacing-1) var(--ks-spacing-2) var(--ks-spacing-1) var(--ks-spacing-6);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-primary);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        outline: none;

        &:focus {
            border-color: var(--ks-border-focus);
        }
    }

    .task-edit-data-scroll {
        flex: 1;
        min-height: 0;
        overflow-y: auto;
        padding: 0 var(--ks-spacing-2) var(--ks-spacing-3);
    }

    .task-edit-data-section-head {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
        width: 100%;
        padding: var(--ks-spacing-2);
        background: transparent;
        border: none;
        cursor: pointer;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        text-transform: uppercase;
        letter-spacing: 0.04em;
    }

    .task-edit-data-chevron {
        display: flex;
        font-size: var(--ks-font-size-xs);
        transition: transform 0.12s;
    }

    .task-edit-data-chevron--open {
        transform: rotate(90deg);
    }

    .task-edit-data-section-label {
        flex: 1;
        text-align: left;
    }

    .task-edit-data-count {
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-muted);
    }

    .task-edit-data-chips {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        padding: 0 var(--ks-spacing-1) var(--ks-spacing-2) var(--ks-spacing-5);
    }

    .task-edit-data-chip {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        text-align: left;
        transition: border-color 0.12s, background-color 0.12s;

        &:hover {
            border-color: var(--ks-text-link);
            background: var(--ks-bg-tag-hover);
        }
    }

    .task-edit-data-chip--static {
        cursor: default;
    }

    .task-edit-data-chip--static:hover {
        border-color: var(--ks-border-subtle);
        background: var(--ks-bg-surface);
    }

    .task-edit-data-chip-label {
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-primary);
    }

    .task-edit-data-chip-type {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-muted);
        text-transform: uppercase;
    }

    .task-edit-data-chip-action {
        flex-shrink: 0;
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-link);
    }

    .task-edit-data-empty {
        padding: var(--ks-spacing-4) var(--ks-spacing-2);
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-muted);
        text-align: center;
        margin: 0;
    }
</style>
