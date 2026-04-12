<script setup lang="ts">
    import {ElCheckTag, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        checked?: boolean
        disabled?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        change: [checked: boolean]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-check-tag
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-check-tag>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/check-tag';
</style>
