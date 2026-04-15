<template>
    <ElCheckboxGroup
        :class="props.size ? `kel-checkbox-group--${props.size}` : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxGroup>
</template>

<script setup lang="ts">
    import {ElCheckboxGroup, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
         
        modelValue?: any[]
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
         
        "update:modelValue": [value: any[]]
         
        change: [value: any[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox-group';
</style>
