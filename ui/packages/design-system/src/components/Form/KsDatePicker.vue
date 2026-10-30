<template>
    <span ref="host" class="ks-picker-host">
        <ElDatePicker
            :modelValue="(model as DatePickerPropsPublic['modelValue'])"
            v-bind="({...filteredProps(), ...pickerAttrs()} as DatePickerPropsPublic)"
            @update:modelValue="(value: T) => (model = value)"
            @change="emit('change', $event)"
        />
    </span>
</template>

<script setup lang="ts" generic="T">
    import {ElDatePicker, type DatePickerPropsPublic} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useInputAttrs} from "../../composables/useInputAttrs"

    defineOptions({inheritAttrs: false})

    const model = defineModel<T>()

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
        change: [value: T]
    }>()

    const filteredProps = useFilteredProps(props)
    const {pickerAttrs} = useInputAttrs("host")
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/date-picker';
    @use 'element-plus/theme-chalk/src/date-picker-panel';
</style>

<style>
    .ks-picker-host {
        display: contents;
    }

    .kel-date-editor.kel-input {
        --kel-date-editor-width: 100%;
        --kel-input-border-color: var(--ks-border-default);
        --kel-input-bg-color: var(--ks-bg-base);
        --kel-input-hover-border-color: var(--ks-border-strong);

        & .kel-input__icon {
            margin-right: .25rem;
        }

        /* The clear icon only mounts on hover, so it sits over space reserved for it instead of widening a shrink-to-fit picker under the cursor. */
        & .kel-input__inner {
            padding-inline-end: var(--ks-spacing-5);
        }

        & .kel-input__suffix {
            position: absolute;
            inset-block: 0;
            inset-inline-end: var(--ks-spacing-3);
        }
    }

    .kel-date-table td.disabled .kel-date-table-cell {
        background: none;
        color: var(--ks-text-inactive);
    }

    .kel-date-picker,
    .kel-date-range-picker {
        --kel-datepicker-hover-text-color: var(--ks-text-link);
    }

    .kel-date-range-picker {
        --kel-datepicker-border-color: var(--ks-border-default);
        --kel-datepicker-inner-border-color: var(--ks-border-default);

        & .kel-date-table th {
            border-bottom-color: var(--ks-border-default);
        }
    }
</style>
