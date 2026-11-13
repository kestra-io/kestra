<template>
    <ElRow v-bind="{...filteredProps(), ...$attrs}">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRow>
</template>

<script setup lang="ts">
    import type {HTMLAttributes} from "vue"
    import {ElRow, type RowProps} from "element-plus"
    import {usePassedProps} from "../../../utils/filteredProps"
    import type {NativeAttributes} from "../../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    const props = defineProps<RowProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof RowProps>>()

    const filteredProps = usePassedProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/row';
</style>

<style>
</style>
