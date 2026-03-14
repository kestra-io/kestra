<script setup lang="ts">
import {ElSelect} from "element-plus"
import type {Component} from "vue"

defineOptions({inheritAttrs: false})

const props = defineProps<{
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    modelValue?: any
    placeholder?: string
    disabled?: boolean
    size?: "small" | "default" | "large"
    filterable?: boolean
    clearable?: boolean
    allowCreate?: boolean
    remote?: boolean
    remoteMethod?: (query: string) => void
    remoteShowSuffix?: boolean
    multiple?: boolean
    collapseTags?: boolean
    persistent?: boolean
    required?: boolean
    valueKey?: string
    placement?: string
    popperOffset?: number
    popperClass?: string
    showArrow?: boolean
    suffixIcon?: Component | string
}>()

const emit = defineEmits<{
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    "update:modelValue": [value: any]
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    change: [value: any]
}>()

defineSlots<{
    default?(): unknown
    prefix?(): unknown
    header?(): unknown
    footer?(): unknown
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    label?(props: {value: any; label: string}): any
    tag?(): unknown
}>()
</script>

<template>
    <el-select
        v-bind="({...props, ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.prefix" #prefix><slot name="prefix" /></template>
        <template v-if="$slots.header" #header><slot name="header" /></template>
        <template v-if="$slots.footer" #footer><slot name="footer" /></template>
        <template v-if="$slots.label" #label="p"><slot name="label" v-bind="p" /></template>
        <template v-if="$slots.tag" #tag><slot name="tag" /></template>
    </el-select>
</template>
