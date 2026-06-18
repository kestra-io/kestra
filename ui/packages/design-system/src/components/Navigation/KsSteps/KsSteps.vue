<template>
    <ElSteps
        :class="{'kel-steps--small': size === 'small'}"
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSteps>
</template>

<script setup lang="ts">
    import {ElSteps} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        active?: number
        direction?: "horizontal" | "vertical"
        space?: string | number
        finishStatus?: string
        processStatus?: string
        simple?: boolean
        alignCenter?: boolean
        size?: "default" | "small"
    }>()

    const filteredProps = useFilteredProps(props, ["size"])

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/steps';
</style>
