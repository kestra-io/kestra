<template>
    <template v-if="shouldShowComparator">
        <div
            v-if="useSegments"
            class="comparator-segments"
            role="group"
            :aria-label="$t('filter.operator')"
        >
            <KsTooltip
                v-for="comparator in filterKey.comparators"
                :key="comparator"
                :content="getLabel(comparator)"
                :disabled="!allHaveGlyphs"
                placement="top"
            >
                <button
                    type="button"
                    class="comparator-segment"
                    :class="{active: comparator === selectedComparator}"
                    :aria-pressed="comparator === selectedComparator"
                    :aria-label="getLabel(comparator)"
                    @click="emits('update:selectedComparator', comparator)"
                >{{ segmentContent(comparator) }}</button>
            </KsTooltip>
        </div>
        <KsDropdown
            v-else
            class="comparator-dropdown"
            trigger="click"
            placement="bottom-start"
        >
            <button type="button" class="comparator-trigger" :aria-label="$t('filter.operator')">
                <span class="comparator-trigger-label">{{ getLabel(selectedComparator) }}</span>
                <ChevronDown class="comparator-trigger-icon" />
            </button>
            <template #dropdown>
                <KsDropdownMenu>
                    <KsDropdownItem
                        v-for="comparator in filterKey.comparators"
                        :key="comparator"
                        :title="getDescription(comparator)"
                        @click="emits('update:selectedComparator', comparator)"
                    >
                        {{ getLabel(comparator) }}
                    </KsDropdownItem>
                </KsDropdownMenu>
            </template>
        </KsDropdown>
    </template>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {
        COMPARATOR_DESCRIPTIONS,
        COMPARATOR_LABELS,
        Comparators,
    } from "../utils/filterTypes"
    import {ChevronDown} from "../utils/icons"

    const {t} = useI18n()

    const COMPARATOR_GLYPHS: Partial<Record<Comparators, string>> = {
        [Comparators.EQUALS]: "=",
        [Comparators.NOT_EQUALS]: "≠",
        [Comparators.GREATER_THAN]: ">",
        [Comparators.LESS_THAN]: "<",
        [Comparators.GREATER_THAN_OR_EQUAL_TO]: "≥",
        [Comparators.LESS_THAN_OR_EQUAL_TO]: "≤",
    }

    const props = defineProps<{
        shouldShowComparator: boolean;
        selectedComparator: Comparators;
        filterKey: {comparators: Comparators[]; comparatorLabels?: Partial<Record<Comparators, string>>};
    }>()

    const emits = defineEmits<{
        "update:selectedComparator": [value: Comparators];
    }>()

    const useSegments = computed(() => props.filterKey.comparators.length <= 3)

    const allHaveGlyphs = computed(() =>
        props.filterKey.comparators.every((c) => c in COMPARATOR_GLYPHS),
    )

    const getLabel = (comparator: Comparators) =>
        props.filterKey.comparatorLabels?.[comparator] ?? COMPARATOR_LABELS[comparator]
    const getDescription = (comparator: Comparators) => t(COMPARATOR_DESCRIPTIONS[comparator])

    const segmentContent = (comparator: Comparators) =>
        allHaveGlyphs.value ? COMPARATOR_GLYPHS[comparator] : getLabel(comparator)
</script>

<style lang="scss" scoped>
.comparator-segments {
    display: flex;
    align-items: center;
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    overflow: hidden;
}

.comparator-segment {
    display: flex;
    align-items: center;
    justify-content: center;
    min-width: 1.75rem;
    height: 1.5rem;
    padding: 0 var(--ks-spacing-2);
    white-space: nowrap;
    background: var(--ks-bg-surface);
    color: var(--ks-text-secondary);
    border: none;
    border-left: 1px solid var(--ks-border-default);
    font-size: var(--ks-font-size-sm);
    cursor: pointer;
    transition: background var(--ks-duration-base) ease, color var(--ks-duration-base) ease;

    &:first-child {
        border-left: none;
    }

    &:hover {
        background: var(--ks-bg-hover);
        color: var(--ks-text-primary);
    }

    &.active {
        background: var(--ks-bg-tag-active);
        color: var(--ks-text-link);
        font-weight: 500;
    }
}

.comparator-trigger {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    height: 1.5rem;
    padding: 0 var(--ks-spacing-2);
    border: 1px solid var(--ks-border-default);
    border-radius: var(--ks-radius-sm);
    background: var(--ks-bg-surface);
    color: var(--ks-text-secondary);
    font-size: var(--ks-font-size-sm);
    cursor: pointer;
    white-space: nowrap;
    transition: background var(--ks-duration-base) ease, color var(--ks-duration-base) ease;
}

.comparator-trigger:hover {
    background: var(--ks-bg-hover);
    color: var(--ks-text-primary);
}

.comparator-trigger-icon {
    display: inline-flex;
    align-items: center;
    font-size: var(--ks-font-size-sm);
}
</style>
