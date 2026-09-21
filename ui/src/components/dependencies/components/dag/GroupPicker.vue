<template>
    <KsDropdown trigger="click" @visibleChange="onVisibleChange">
        <KsButton
            size="small"
            class="trigger"
            :title="$t('dependency.dag.group_by')"
        >
            <SelectGroup />
            <span class="label">{{ pinnedLabel ?? $t("dependency.dag.groups", {n: groups.length}) }}</span>
            <ChevronDown />
        </KsButton>

        <template #dropdown>
            <KsDropdownMenu class="picker">
                <div class="search">
                    <KsSearch
                        v-model="query"
                        :placeholder="$t('search')"
                        :aria-label="$t('dependency.dag.group_by')"
                    />
                </div>

                <KsScrollbar :maxHeight="240">
                    <KsDropdownItem
                        v-for="group in matches"
                        :key="group.key"
                        @click="onSelect(group.key)"
                        @mouseenter="emit('preview', group.key)"
                        @mouseleave="onPreviewOut"
                        @focus="emit('preview', group.key)"
                        @blur="onPreviewOut"
                    >
                        <span :class="['row', {active: group.key === activeGroup}]">
                            <KsIcon size="xs" class="check">
                                <Check />
                            </KsIcon>
                            <span class="name">{{ group.label }}</span>
                            <span class="count">{{ group.count }}</span>
                        </span>
                    </KsDropdownItem>

                    <KsText
                        v-if="!matches.length"
                        size="small"
                        class="empty"
                    >
                        {{ $t("dependency.search.no_results", {term: query}) }}
                    </KsText>
                </KsScrollbar>
            </KsDropdownMenu>
        </template>
    </KsDropdown>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import SelectGroup from "vue-material-design-icons/SelectGroup.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import Check from "vue-material-design-icons/Check.vue"

    const props = defineProps<{
        groups: {key: string; label: string; count: number}[];
        activeGroup?: string;
    }>()

    const emit = defineEmits<{
        /** Fades everything outside a group without pinning it; undefined restores. */
        preview: [key: string | undefined];
        toggle: [key: string];
    }>()

    const query = ref("")

    const matches = computed(() => {
        const needle = query.value.trim().toLowerCase()
        if (!needle) {
            return props.groups
        }
        return props.groups.filter(({label}) => label.toLowerCase().includes(needle))
    })

    /** The pinned group's own name; the trigger falls back to the group count. */
    const pinnedLabel = computed(() => props.groups.find(({key}) => key === props.activeGroup)?.label)

    let closedBySelect = false

    const onSelect = (key: string): void => {
        closedBySelect = true
        emit("toggle", key)
    }

    // Skipped after a select: re-emitting `activeGroup` would read the not-yet-updated prop and undo the toggle.
    const onPreviewOut = (): void => {
        if (closedBySelect) {
            return
        }
        emit("preview", props.activeGroup)
    }

    /** Closing while a row was hovered would leave the graph faded against an unpinned group. */
    const onVisibleChange = (visible: boolean): void => {
        if (visible) {
            return
        }
        query.value = ""
        if (closedBySelect) {
            closedBySelect = false
            return
        }
        emit("preview", props.activeGroup)
    }
</script>

<style lang="scss" scoped>
    .picker {
        min-width: 16rem;

        .search {
            padding: var(--ks-spacing-1) var(--ks-spacing-2) var(--ks-spacing-2);
        }

        .row {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            width: 100%;
            min-width: 0;

            .check {
                visibility: hidden;
                color: var(--ks-text-link);
                flex: 0 0 auto;
            }

            &.active .check {
                visibility: visible;
            }

            .name {
                flex: 1 1 auto;
                min-width: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }

            .count {
                flex: 0 0 auto;
                color: var(--ks-text-muted);
            }
        }

        .empty {
            display: block;
            padding: var(--ks-spacing-2);
            color: var(--ks-text-muted);
        }
    }

    .trigger {
        height: 1.875rem;

        .label {
            margin: 0 var(--ks-spacing-1) 0 var(--ks-spacing-2);
            max-width: 8rem;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
        }
    }
</style>
