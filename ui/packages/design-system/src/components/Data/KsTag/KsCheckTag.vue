<template>
    <ElCheckTag
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="`kel-check-tag--${size}`"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckTag>
</template>

<script setup lang="ts">
    import {ElCheckTag} from "element-plus"

    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        checked?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>(), {
        size: "small",
    })

    const emit = defineEmits<{
        change: [checked: boolean]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["size"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/check-tag';

    .kel-check-tag {
        &--small {
            --kel-font-size-base: var(--ks-font-size-xs);
            padding: 3px 8px;
        }

        &--default {
            --kel-font-size-base: var(--ks-font-size-base);
            padding: 7px 15px;
        }

        &--large {
            --kel-font-size-base: var(--ks-font-size-md);
            padding: 9px 18px;
        }
    }
</style>
