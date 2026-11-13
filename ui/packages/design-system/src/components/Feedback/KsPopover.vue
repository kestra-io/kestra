<template>
    <ElPopover
        :persistent="false"
        :hideAfter="0"
        transition=""
        :visible="internalVisible"
        :showArrow="showArrow"
        v-bind="({...filteredProps(), ...$attrs} as PopoverProps)"
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
    import type {PopoverProps} from "element-plus"
    import {onBeforeUnmount, ref, watch, type HTMLAttributes} from "vue"
    import {usePassedProps} from "../../utils/filteredProps"
    import type {NativeAttributes} from "../../utils/nativeAttributes"

    defineOptions({inheritAttrs: false})

    // Element Plus types its listeners as props; declared here, they would stop being the parent's listeners.
    export type KsPopoverProps = Omit<PopoverProps, "onUpdate:visible" | "onMouseenter" | "onMouseleave" | "onClick" | "onKeydown" | "onFocus" | "onBlur" | "onContextmenu">

    const props = withDefaults(defineProps<KsPopoverProps & /* @vue-ignore */ Omit<NativeAttributes<HTMLAttributes>, keyof KsPopoverProps>>(), {visible: undefined, showArrow: false})

    const emit = defineEmits<{
        "update:visible": [value: boolean]
        hide: []
    }>()

    defineSlots<{
        default?(): unknown
        reference?(): unknown
    }>()

    const internalVisible = ref(false)

    // ElTooltip does not wire up Escape-to-close itself.
    function handleEscapeKeydown(event: KeyboardEvent) {
        if (event.key === "Escape") handleUpdateVisible(false)
    }

    // Must be declared before the props.visible watch below, and without `immediate`.
    watch(internalVisible, (visible) => {
        if (visible) document.addEventListener("keydown", handleEscapeKeydown)
        else document.removeEventListener("keydown", handleEscapeKeydown)
    })

    watch(() => props.visible, (val) => {
        if (val !== undefined && val !== null) internalVisible.value = val
    }, {immediate: true})

    function handleUpdateVisible(v: boolean) {
        internalVisible.value = v
        emit("update:visible", v)
    }

    onBeforeUnmount(() => document.removeEventListener("keydown", handleEscapeKeydown))

    const filteredProps = usePassedProps(props, ["visible"])
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/popover';
</style>

<style>
</style>
