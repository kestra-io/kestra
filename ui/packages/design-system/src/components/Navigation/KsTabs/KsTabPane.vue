<template>
    <ElTabPane
        v-bind="({...filteredProps(), ...$attrs} as TabPaneProps)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.label" #label>
            <slot name="label" />
        </template>
    </ElTabPane>
</template>

<script setup lang="ts">
    import {ElTabPane, type TabPaneProps} from "element-plus"
    import type {HTMLAttributes} from "vue"
    import {usePassedProps} from "../../../utils/filteredProps"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    const props = defineProps<TabPaneProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof TabPaneProps>>()

    const filteredProps = usePassedProps(props)

    defineSlots<{
        default?(): unknown
        label?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tab-pane';
</style>

<style>
</style>
