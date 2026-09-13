<template>
    <ElOption v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots['default']" #default>
            <slot />
        </template>
        <template v-else-if="color" #default>
            <span class="kel-select-color-option" :style="{color}">{{ label }}</span>
        </template>
    </ElOption>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {ElOption} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import {KsSelectColorMapKey} from "./colorMap"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value: any
        label?: string | number
        disabled?: boolean
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)

    const colorMap = inject(KsSelectColorMapKey, undefined)
    const color = computed(() => colorMap?.value?.[props.value])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/option';
</style>
