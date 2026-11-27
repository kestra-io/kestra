<template>
    <span ref="host" class="ks-picker-host">
        <ElDatePicker
            :modelValue="(shown as DatePickerPropsPublic['modelValue'])"
            v-bind="({...filteredProps(), ...pickerAttrs(), ...zonedAttrs} as DatePickerPropsPublic)"
            @update:modelValue="(value: T) => emit('update:modelValue', fromPicker(value))"
            @change="(value: DatePickerPropsPublic['modelValue']) => emit('change', fromPicker(value as T))"
        />
    </span>
</template>

<script setup lang="ts" generic="T">
    import {type DatePickerPropsPublic, type ElTooltipContentProps} from "element-plus"
    import {ElDatePicker} from "../../utils/elementPlusListeners"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useInputAttrs} from "../../composables/useInputAttrs"
    import type {NativeAttributes} from "../../utils/nativeAttributes"
    import {computed, type HTMLAttributes} from "vue"
    import {instantToPickerDate, pickerDateToInstant} from "../../utils/date"

    defineOptions({inheritAttrs: false})

    type KsDatePickerProps = {
        type?: string
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        disabledDate?: (date: Date) => boolean
        unlinkPanels?: boolean
        size?: "large" | "default" | "small"
        startPlaceholder?: string
        endPlaceholder?: string
        /** Shows and edits a single date as a wall clock in this IANA zone instead of the browser's; the model stays an instant. */
        timezone?: string
    }

    type PickerTooltipProps = Pick<ElTooltipContentProps, "teleported">

    const props = withDefaults(defineProps<KsDatePickerProps & {modelValue?: T} & /* @vue-ignore */ Omit<NativeAttributes<DatePickerPropsPublic & PickerTooltipProps & HTMLAttributes>, keyof KsDatePickerProps | "modelValue">>(), {
        type: undefined,
        placeholder: undefined,
        clearable: undefined,
        disabledDate: undefined,
        size: undefined,
        startPlaceholder: undefined,
        endPlaceholder: undefined,
        timezone: undefined,
    })

    const emit = defineEmits<{
        "update:modelValue": [value: T]
        change: [value: T]
    }>()

    const filteredProps = useFilteredProps(props, ["modelValue", "timezone"])
    const {pickerAttrs} = useInputAttrs("host")

    const shown = computed(() => props.timezone ? instantToPickerDate(props.modelValue, props.timezone) : props.modelValue)

    // Element Plus fills "Now" with the browser's wall clock, which would then be read in the other zone.
    const zonedAttrs = computed(() => props.timezone ? {showNow: false} : {})

    function fromPicker(value: T): T {
        return props.timezone ? pickerDateToInstant(value as Date | null, props.timezone) as T : value
    }
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
        --kel-input-border-color: var(--ks-input-border-color, var(--ks-border-default));
        --kel-input-bg-color: var(--ks-bg-base);
        --kel-input-hover-border-color: var(--ks-input-border-color, var(--ks-border-strong));

        & .kel-input__icon {
            margin-right: .25rem;
        }

        /* The clear icon only mounts on hover, so it sits over space reserved for it instead of widening a shrink-to-fit picker under the cursor. */
        & .kel-input__inner {
            font-size: var(--ks-input-font-size, inherit);
            padding-inline-end: var(--ks-spacing-5);
        }

        & .kel-input__suffix {
            position: absolute;
            inset-block: 0;
            inset-inline-end: var(--ks-spacing-3);
        }
    }

    .kel-date-editor.kel-range-editor .kel-range-input {
        font-size: var(--ks-input-font-size, var(--kel-font-size-base));
    }

    .kel-date-editor.kel-range-editor--small .kel-range-input {
        font-size: var(--ks-input-font-size, 12px);
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
