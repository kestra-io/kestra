<template>
    <ElBacktop
        v-bind="{...filteredProps(), ...$attrs}"
        @click="(evt) => emit('click', evt)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElBacktop>
</template>

<script setup lang="ts">
    import {ElBacktop, type BacktopProps} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<BacktopProps>()

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
