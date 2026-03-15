<script setup lang="ts">
    import {ElOption, provideGlobalConfig} from "element-plus"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        value: any
        label?: string | number | boolean
        disabled?: boolean
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-option v-bind="({...$props, ...$attrs} as any)">
        <template v-if="$slots['default']" #default>
            <slot />
        </template>
    </el-option>
</template>
