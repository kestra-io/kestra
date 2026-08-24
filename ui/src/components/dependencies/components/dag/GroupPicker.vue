<template>
    <KsPopover
        v-model:visible="isOpen"
        placement="bottom-start"
        trigger="click"
        :width="260"
        :showArrow="false"
        @hide="onHide"
    >
        <div class="group-picker">
            <!-- Fixed above the scroll area: with hundreds of groups the search is the only
                 way in, so it must never scroll out of reach. -->
            <KsSearch
                v-model="query"
                class="group-search"
                :aria-label="$t('dependency.dag.group_by')"
            />

            <KsScrollbar class="group-list" :maxHeight="240">
                <button
                    v-for="group in matches"
                    :key="group.key"
                    type="button"
                    :class="['group-row', {'is-active': group.key === activeGroup}]"
                    @mouseenter="emit('preview', group.key)"
                    @mouseleave="onPreviewOut"
                    @focus="emit('preview', group.key)"
                    @blur="onPreviewOut"
                    @click="onSelect(group.key)"
                >
                    <KsIcon v-if="group.key === activeGroup" size="xs" class="group-check">
                        <Check />
                    </KsIcon>
                    <span class="group-name">{{ group.label }}</span>
                    <span class="group-count">{{ group.count }}</span>
                </button>

                <KsText v-if="!matches.length" size="small" class="group-empty">
                    {{ $t("dependency.search.no_results", {term: query}) }}
                </KsText>
            </KsScrollbar>
        </div>

        <template #reference>
            <KsButton size="small" class="group-trigger" :title="$t('dependency.dag.group_by')">
                <SelectGroup />
                <span class="group-trigger-label">{{ triggerLabel }}</span>
                <ChevronDown />
            </KsButton>
        </template>
    </KsPopover>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"

    import SelectGroup from "vue-material-design-icons/SelectGroup.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Check from "vue-material-design-icons/Check.vue"

    export interface GroupEntry {
        key: string;
        label: string;
        count: number;
    }

    const props = defineProps<{
        groups: GroupEntry[];
        activeGroup?: string;
    }>()

    const emit = defineEmits<{
        /** Fades everything outside a group without pinning it; undefined restores. */
        preview: [key: string | undefined];
        toggle: [key: string];
    }>()

    const isOpen = ref(false)
    const query = ref("")

    const matches = computed(() => {
        const needle = query.value.trim().toLowerCase()
        if (!needle) return props.groups
        return props.groups.filter(({label}) => label.toLowerCase().includes(needle))
    })

    /**
     * The pinned group's own name when there is one, otherwise the number of groups. With
     * no chip row this button is the only place the pinned state is visible, so naming the
     * group beats a bare count.
     */
    const triggerLabel = computed(() => {
        const pinned = props.groups.find(({key}) => key === props.activeGroup)
        return pinned ? pinned.label : String(props.groups.length)
    })

    /**
     * Picking a group closes the panel. Leaving it open forces the user to dismiss it by
     * clicking the canvas, and that click also clears the selection.
     */
    let closedBySelect = false

    const onSelect = (key: string): void => {
        closedBySelect = true
        emit("toggle", key)
        isOpen.value = false
    }

    /**
     * Leaving a row restores the pinned group, but not once a selection has closed the
     * popover: toggling already applied the right isolation, and re-emitting `activeGroup`
     * here would read the not-yet-updated prop and undo it.
     */
    const onPreviewOut = (): void => {
        if (closedBySelect) return
        emit("preview", props.activeGroup)
    }

    /** Closing while a row was hovered would leave the graph faded against an unpinned group. */
    const onHide = (): void => {
        query.value = ""
        if (closedBySelect) {
            closedBySelect = false
            return
        }
        emit("preview", props.activeGroup)
    }
</script>

<style lang="scss" scoped>
    // No padding of its own: the popover's default inset applies, since KsPopover exposes
    // no compliant way to strip it and a utility class (p-0) is banned in feature code.
    .group-picker {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
    }

    .group-list {
        width: 100%;
    }

    .group-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 100%;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border: none;
        border-radius: var(--ks-radius-base);
        background: transparent;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        cursor: pointer;
        text-align: left;
    }

    .group-row:hover {
        background: var(--ks-bg-hover);
        color: var(--ks-text-primary);
    }

    .group-row.is-active {
        color: var(--ks-text-primary);
    }

    .group-check {
        color: var(--ks-text-link);
        flex: 0 0 auto;
    }

    .group-name {
        flex: 1 1 auto;
        min-width: 0;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .group-count {
        flex: 0 0 auto;
        color: var(--ks-text-muted);
    }

    .group-empty {
        display: block;
        padding: var(--ks-spacing-2);
        color: var(--ks-text-muted);
    }

    // A small KsButton is a fixed 24px, which beats the toolbar's align-items: stretch and
    // leaves this sitting shorter than the grouping select beside it (that carries a 30px
    // floor for every size). Releasing the height lets the row settle on one height.
    .group-trigger {
        align-self: stretch;
        height: auto;
    }

    .group-trigger-label {
        max-width: 8rem;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }
</style>
