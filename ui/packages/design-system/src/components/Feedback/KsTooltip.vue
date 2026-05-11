<template>
    <ElTooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        :effect="effectValue"
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.content" #content>
            <slot name="content" />
        </template>
    </ElTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {ElTooltip} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useTheme} from "../../composables/useTheme"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        content?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu" | "manual"
        placement?: string
        effect?: "light" | "dark"
        enterable?: boolean
        rawContent?: boolean
        disabled?: boolean
        autoClose?: boolean | number
    }>(), {
        content: undefined,
        trigger: undefined,
        placement: undefined,
        effect: undefined,
        enterable: undefined,
        autoClose: undefined,
    })

    defineSlots<{
        default?(): unknown
        content?(): unknown
    }>()

    const {isDark} = useTheme()

    const effectValue = computed(() => props.effect ?? (isDark.value ? "light" : "dark"))

    const filteredProps = useFilteredProps(props, ["effect"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tooltip';
</style>
