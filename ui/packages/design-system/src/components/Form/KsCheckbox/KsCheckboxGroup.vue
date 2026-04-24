<template>
    <ElCheckboxGroup
        v-model="model"
        :class="props.size ? `kel-checkbox-group--${props.size}` : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxGroup>
</template>

<script setup lang="ts">
    import {ElCheckboxGroup} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any[]>()

    const props = defineProps<{
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>()

    const emit = defineEmits<{
        change: [value: any[]]
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
