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
    import {onBeforeUnmount, ref, watch} from "vue"
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

    // ElTooltip (which ElPopover wraps) never wires up Escape-to-close itself — see
    // https://github.com/element-plus/element-plus, tooltip/trigger.vue only reacts to `triggerKeys`
    // (Enter/Space to open). Close explicitly so every KsPopover consumer gets it for free.
    function handleEscapeKeydown(event: KeyboardEvent) {
        if (event.key === "Escape") handleUpdateVisible(false)
    }

    watch(internalVisible, (visible) => {
        if (visible) document.addEventListener("keydown", handleEscapeKeydown)
        else document.removeEventListener("keydown", handleEscapeKeydown)
    })

    onBeforeUnmount(() => document.removeEventListener("keydown", handleEscapeKeydown))

    const filteredProps = useFilteredProps(props, ["visible"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/popover';
</style>
