<template>
    <ElBacktop
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="(evt) => emit('click', evt)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElBacktop>
</template>

<script setup lang="ts">
    import {ElBacktop} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        visibilityHeight?: number
        target?: string
        right?: number
        bottom?: number
    }>()

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/backtop';
</style>
