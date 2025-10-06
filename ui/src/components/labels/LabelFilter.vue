<template>
    <el-select
        :modelValue="labels"
        @update:model-value="onInput"
        multiple
        filterable
        allowCreate
        clearable
        collapseTags
        collapseTagsTooltip
        defaultFirstOption
        :persistent="false"
        :reserveKeyword="false"
        @focus="hover = true"
        @blur="hover = false"
        :placeholder="hover ? $t('label filter placeholder') : $t('labels')"
    >
        <el-option
            v-for="label in labels"
            :key="label"
            :label="label"
            :value="label"
        />
    </el-select>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    const isValidLabel = (label: string): boolean => {
        return label.match(".+:.+") !== null;
    };

    const props = defineProps<{
        modelValue?: string | string[];
    }>();

    const emit = defineEmits<{
        "update:modelValue": [value: string[]];
    }>();

    const asArrayProp = (unknownValue: string | string[] | undefined): string[] => {
        return (!Array.isArray(unknownValue) && unknownValue !== undefined) ? [unknownValue] : (unknownValue ?? []);
    };

    const hover = ref<boolean>(false);
    const labels = ref<string[]>(asArrayProp(props.modelValue));

    watch(() => props.modelValue, (newValue) => {
        labels.value = asArrayProp(newValue);
    });

    const onInput = (value: string[]) => {
        labels.value = value.filter((label) => isValidLabel(label));
        emit("update:modelValue", labels.value);
    };
</script>