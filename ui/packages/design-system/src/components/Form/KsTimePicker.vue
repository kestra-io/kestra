<template>
    <span ref="host" class="ks-picker-host">
        <ElTimePicker
            v-model="model"
            v-bind="({...filteredProps(), ...pickerAttrs()} as TimePickerDefaultPropsPublic)"
            @change="emit('change', $event)"
        />
    </span>
</template>

<script setup lang="ts">
    import {ElTimePicker, type TimePickerDefaultPropsPublic, type ElTooltipContentProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useInputAttrs} from "../../composables/useInputAttrs"
    import type {NativeAttributes} from "../../utils/nativeAttributes"
    import type {HTMLAttributes} from "vue"

    defineOptions({inheritAttrs: false})

    const model = defineModel<Date | string | null>()

    export interface KsTimePickerProps {
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        format?: string
        valueFormat?: string
        size?: "large" | "default" | "small"
    }

    // The picker hands its attrs to its tooltip, which is where `teleported` lands.
    type PickerTooltipProps = Pick<ElTooltipContentProps, "teleported">

    const props = withDefaults(defineProps<KsTimePickerProps & /* @vue-ignore */ Omit<NativeAttributes<TimePickerDefaultPropsPublic & PickerTooltipProps & HTMLAttributes>, keyof KsTimePickerProps | "modelValue">>(), {
        placeholder: undefined,
        clearable: undefined,
        format: undefined,
        valueFormat: undefined,
        size: undefined,
    })

    const emit = defineEmits<{
        change: [value: Date | string | null]
    }>()

    const filteredProps = useFilteredProps(props)
    const {pickerAttrs} = useInputAttrs("host")
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/time-picker';
</style>

<style>
    .ks-picker-host {
        display: contents;
    }
</style>
