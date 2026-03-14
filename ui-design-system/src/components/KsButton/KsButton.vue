<script setup lang="ts">
import {ElButton} from "element-plus"
import type {Component} from "vue"

defineOptions({inheritAttrs: false})

defineProps<{
    type?: "default" | "primary" | "success" | "warning" | "info" | "danger" | "text" | ""
    size?: "small" | "default" | "large" | ""
    disabled?: boolean
    icon?: string | Component
    nativeType?: "button" | "submit" | "reset"
    loading?: boolean
    loadingIcon?: string | Component
    plain?: boolean
    text?: boolean
    link?: boolean
    bg?: boolean
    autofocus?: boolean
    round?: boolean
    circle?: boolean
    color?: string
    dark?: boolean
    autoInsertSpace?: boolean
    tag?: string | Component
}>()

const emit = defineEmits<{
    click: [evt: MouseEvent]
}>()

defineSlots<{
    default?(): unknown
    loading?(): unknown
    icon?(): unknown
}>()
</script>

<template>
    <el-button
        v-bind="({...$props, ...$attrs} as any)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.loading" #loading><slot name="loading" /></template>
        <template v-if="$slots.icon" #icon><slot name="icon" /></template>
    </el-button>
</template>
