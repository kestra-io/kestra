<template>
    <ElDatePicker
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    />
</template>

<script setup lang="ts">
    import {ElDatePicker, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: Date | Date[] | string | string[] | null
        type?: string
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        format?: string
        valueFormat?: string
        disabledDate?: (date: Date) => boolean
        disabledTime?: (date: Date) => object
        unlinkPanels?: boolean
        size?: "large" | "default" | "small"
        startPlaceholder?: string
        endPlaceholder?: string
    }>(), {
        clearable: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: any]
        change: [value: any]
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/date-picker';
    @use 'element-plus/theme-chalk/src/date-picker-panel';

    .kel-date-editor {
        --kel-input-border-color: var(--ks-border-primary);
        --kel-input-bg-color: var(--ks-background-input);

        .kel-input {
            background-color: var(--ks-background-body);
            width: 100%;
        }

        .kel-input__icon {
            margin-right: .25rem;
        }
    }

    .kel-date-table td.disabled .kel-date-table-cell {
        background: none;
        color: var(--ks-content-inactive);
    }

    .kel-date-range-picker {
        --kel-datepicker-border-color: var(--ks-border-primary);
        --kel-datepicker-inner-border-color: var(--ks-border-primary);

        .kel-date-table th {
            border-bottom-color: var(--ks-border-primary);
        }
    }
</style>