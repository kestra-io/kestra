<script setup lang="ts">
    import {ElEmpty, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        image?: string
        imageSize?: number
        description?: string
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        description?(): unknown
        image?(): unknown
    }>()
</script>

<template>
    <el-empty v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.description" #description><slot name="description" /></template>
        <template v-if="$slots.image" #image><slot name="image" /></template>
    </el-empty>
</template>

<style lang="scss">
    .kel-empty {
        background-color: var(--ks-background-card);
    }
</style>