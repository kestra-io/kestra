<template>
    <ElDatePicker
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    />
</template>

<script setup lang="ts">
    import {ElDatePicker} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = withDefaults(defineProps<{
        type?: string
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        disabledDate?: (date: Date) => boolean
        unlinkPanels?: boolean
        size?: "large" | "default" | "small"
        startPlaceholder?: string
        endPlaceholder?: string
    }>(), {
        type: undefined,
        placeholder: undefined,
        clearable: undefined,
        disabledDate: undefined,
        size: undefined,
        startPlaceholder: undefined,
        endPlaceholder: undefined,
    })

    const emit = defineEmits<{
        change: [value: any]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/date-picker';
    @use 'element-plus/theme-chalk/src/date-picker-panel';

    .kel-date-editor.kel-input {
        --kel-date-editor-width: 100%;
        --kel-input-border-color: var(--ks-border-primary);
        --kel-input-bg-color: var(--ks-background-panel);

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
