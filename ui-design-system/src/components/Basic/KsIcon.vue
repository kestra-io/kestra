<script setup lang="ts">
    import {ElIcon, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        size?: number | string
        color?: string
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        click: [evt: MouseEvent]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-icon
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-icon>
</template>
