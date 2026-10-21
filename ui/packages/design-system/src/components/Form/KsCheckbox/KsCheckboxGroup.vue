<template>
    <ElCheckboxGroup
        v-model="model"
        :class="props.size ? `kel-checkbox-group--${props.size}` : undefined"
        v-bind="({...filteredProps(), ...$attrs} as CheckboxGroupProps)"
        @change="emit('change', $event as T[])"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxGroup>
</template>

<script setup lang="ts" generic="T extends string | number">
    import {ElCheckboxGroup, type CheckboxGroupProps} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<T[]>()

    const props = defineProps<{
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>()

    const emit = defineEmits<{
        change: [value: T[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox-group';
</style>
