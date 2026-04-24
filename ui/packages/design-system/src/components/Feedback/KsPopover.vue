<template>
    <ElPopover
        :persistent="false"
        :hideAfter="0"
        transition=""
        :visible="internalVisible"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:visible="handleUpdateVisible"
        @hide="emit('hide')"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.reference" #reference>
            <slot name="reference" />
        </template>
    </ElPopover>
</template>

<script setup lang="ts">
    import {ElPopover} from "element-plus"
    import {ref, watch} from "vue"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        visible?: boolean
        placement?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu"
        width?: number | string
        effect?: "light" | "dark"
        popperClass?: string
        showArrow?: boolean
        disabled?: boolean
        title?: string
        content?: string
    }>()

    const emit = defineEmits<{
        "update:visible": [value: boolean]
        hide: []
    }>()

    defineSlots<{
        default?(): unknown
        reference?(): unknown
    }>()

    const internalVisible = ref(false)

    watch(() => props.visible, (val) => {
        if (val !== undefined) internalVisible.value = val
    }, {immediate: true})

    function handleUpdateVisible(v: boolean) {
        internalVisible.value = v
        emit("update:visible", v)
    }

    const filteredProps = useFilteredProps(props, ["visible"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/popover';
</style>
