<template>
    <div v-if="shouldShowComparator" class="comparator-container">
        <label class="form-label">Filter Operator</label>
        <el-select
            v-model="comparatorModel"
            placeholder="Select operator"
            class="select-full-width"
        >
            <el-option
                v-for="comparator in filterKey.comparators"
                :key="comparator"
                :label="getComparatorLabel(comparator)"
                :value="comparator"
            >
                <div class="comparator-option">
                    <div class="comparator-label">
                        {{ getComparatorLabel(comparator) }}
                    </div>
                    <div class="comparator-description">
                        {{ getComparatorDescription(comparator) }}
                    </div>
                </div>
            </el-option>
        </el-select>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {COMPARATOR_LABELS, COMPARATOR_DESCRIPTIONS} from "../utils/types";
    import {Comparators} from "../../../composables/monaco/languages/filters/filterCompletion";

    const props = defineProps<{
        shouldShowComparator: boolean;
        selectedComparator: Comparators;
        filterKey: {
            comparators: Comparators[];
        };
    }>();

    const emits = defineEmits<{
        "update:selectedComparator": [value: Comparators];
    }>();

    const comparatorModel = computed({
        get: () => props.selectedComparator,
        set: (value: Comparators) => emits("update:selectedComparator", value)
    });

    const getComparatorLabel = (comparator: Comparators) =>
        COMPARATOR_LABELS[comparator];

    const getComparatorDescription = (comparator: Comparators) =>
        COMPARATOR_DESCRIPTIONS[comparator];
</script>

<style lang="scss" scoped>
.comparator-container {
    padding-left: 1rem;
    padding-right: 1rem;

    .form-label {
        display: block;
        font-size: 12px;
        font-weight: 600;
        margin: 0.5rem 0;
        color: var(--ks-content-tertiary);
    }

    .select-full-width {
        width: 100%;
    }
}

.comparator-option {
    padding: 4px 0;

    .comparator-label {
        font-size: 14px;
        line-height: 1.2;
    }

    .comparator-description {
        color: var(--ks-content-tertiary);
        font-size: 12px;
        line-height: 1.3;
    }
}

.el-select-dropdown__item {
    height: fit-content;
    padding: 4px 12px;

    &.is-selected {
        color: var(--ks-chart-success);
        background-color: var(--el-color-success-light-9);
    }
}
</style>