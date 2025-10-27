<template>
    <div class="radio">
        <div class="option" :class="{selected: selectedOption === 'ALL'}" @click="selectOption('ALL')">
            <div class="content">
                <span class="title">{{ $t("filter.hierarchy.all") }}</span>
                <span class="desc">{{ $t("filter.show default") }}</span>
            </div>
            <el-radio :modelValue="selectedOption" :value="'ALL'" />
        </div>

        <div
            v-for="option in options"
            :key="option.value"
            class="option"
            :class="{selected: selectedOption === option.value}"
            @click="selectOption(option.value)"
        >
            <div class="content">
                <span class="title">{{ option.label }}</span>
                <span v-if="option.description" class="desc">{{ option.description }}</span>
            </div>
            <el-radio :modelValue="selectedOption" :value="option.value" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";
    import type {FilterValue} from "../../utils/filterTypes";

    const props = defineProps<{
        modelValue: string;
        options: FilterValue[];
    }>();

    const emits = defineEmits<{
        "update:modelValue": [value: string];
    }>();

    const selectedOption = ref(props.modelValue);

    watch(() => props.modelValue, (newValue) => {
        selectedOption.value = newValue;
    });

    const selectOption = (option: string) => {
        selectedOption.value = option;
        emits("update:modelValue", option);
    };
</script>

<style lang="scss" scoped>
.radio {
    padding: 1rem;
    display: flex;
    flex-direction: column;
    gap: 0.35rem;

    .option {
        cursor: pointer;
        transition: background-color 0.2s;
        padding: 0.25rem 0.75rem;
        padding-right: 4px;
        border-radius: 0.25rem;
        border: 1px solid var(--ks-border-primary);
        display: flex;
        align-items: center;
        justify-content: space-between;

        &.selected {
            border-color: var(--ks-content-link);
        }

        &:hover {
            background-color: var(--ks-dropdown-background-hover);
        }

        :deep(.el-radio) {
            margin-right: 0;
            height: 2.5rem;

            .el-radio__inner {
                width: 1.15rem;
                height: 1.15rem;
                border: 0.125rem solid var(--ks-content-link);
                background: transparent;

                &::after {
                    width: 0.5rem;
                    height: 0.5rem;
                    background-color: var(--ks-content-link);
                }
            }

            &.is-checked {
                .el-radio__label {
                    color: var(--ks-content-link);
                }

                .el-radio__inner {
                    border-color: var(--ks-content-link);
                    background: transparent;
                }
            }

            &:hover {
                .el-radio__label {
                    color: var(--ks-content-link-hover);
                }

                .el-radio__inner {
                    border-color: var(--ks-content-link-hover);
                }
            }
        }

        .content {
            display: flex;
            flex-direction: column;
            flex: 1;
            padding-right: .5rem;

            .title {
                font-size: 0.875rem;
                font-weight: 500;
                color: var(--ks-content-primary);
            }

            .desc {
                font-size: 0.75rem;
                color: var(--ks-content-tertiary);
                line-height: 1.4;
            }
        }
    }
}
</style>
