<template>
    <div v-if="shouldShowComparator" class="comp-container">
        <label class="label">{{ t("filter.operator") }}</label>
        <el-select
            v-model="comparatorModel"
            class="select"
        >
            <el-option
                v-for="comparator in filterKey.comparators"
                :key="comparator"
                :label="getLabel(comparator)"
                :value="comparator"
            >
                <div class="option">
                    <div class="comp-label">
                        {{ getLabel(comparator) }}
                    </div>
                    <div class="comp-desc">
                        {{ getDescription(comparator) }}
                    </div>
                </div>
            </el-option>
        </el-select>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {
        COMPARATOR_DESCRIPTIONS,
        COMPARATOR_LABELS,
        Comparators
    } from "../../utils/filterTypes";

    const {t} = useI18n();

    const props = defineProps<{
        shouldShowComparator: boolean;
        selectedComparator: Comparators;
        filterKey: {comparators: Comparators[]};
    }>();

    const emits = defineEmits<{
        "update:selectedComparator": [value: Comparators];
    }>();

    const comparatorModel = computed({
        get: () => props.selectedComparator,
        set: (value: Comparators) => emits("update:selectedComparator", value)
    });

    const getLabel = (comparator: Comparators) => COMPARATOR_LABELS[comparator];
    const getDescription = (comparator: Comparators) => t(COMPARATOR_DESCRIPTIONS[comparator]);
</script>

<style lang="scss" scoped>
.comp-container {
    padding-left: 1rem;
    padding-right: 1rem;

    .label {
        display: block;
        font-size: 12px;
        font-weight: 500;
        margin: 0.25rem 0;
        color: var(--ks-content-tertiary);
    }

    .select {
        width: 100%;
    }
}

.option {
    padding: 4px 0;

    .comp-label {
        font-size: 14px;
        line-height: 1.2;
    }

    .comp-desc {
        color: var(--ks-content-tertiary);
        font-size: 12px;
        line-height: 1.3;
    }
}

.el-select-dropdown__item {
    height: fit-content;
    padding: 4px 12px;
}
</style>