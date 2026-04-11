<script setup lang="ts">
    import {ElTooltip, provideGlobalConfig} from "element-plus"
    import {computed} from "vue"
    import {useFilteredProps} from "../../utils/filteredProps"
    import {useTheme} from "../../composables/useTheme"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        content?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu" | "manual"
        placement?: string
        effect?: "light" | "dark"
        enterable?: boolean
        rawContent?: boolean
        disabled?: boolean,
        autoClose?: boolean | number
    }>(), {
        effect: "dark",
        enterable: undefined,
        autoClose: undefined,
    })

    const {isDark} = useTheme()

    const effectValue = computed(() => props.effect ?? (isDark.value ? "light" : "dark"))

    const filteredProps = useFilteredProps(props, ["effect"])

    defineSlots<{
        default?(): unknown
        content?(): unknown
    }>()
</script>

<template>
    <el-tooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        :effect="effectValue"
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.content" #content><slot name="content" /></template>
    </el-tooltip>
</template>
