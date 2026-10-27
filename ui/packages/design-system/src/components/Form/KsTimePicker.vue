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
    import {ElTimePicker, type TimePickerDefaultPropsPublic} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useInputAttrs} from "../../composables/useInputAttrs"

    defineOptions({inheritAttrs: false})

    const model = defineModel<Date | string | null>()

    const props = withDefaults(defineProps<{
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        format?: string
        valueFormat?: string
        size?: "large" | "default" | "small"
    }>(), {
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
