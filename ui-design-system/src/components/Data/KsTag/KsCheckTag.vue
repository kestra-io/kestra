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
    import {ElCheckTag, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        checked?: boolean
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>(), {
        size: "small",
    })

    const filteredProps = useFilteredProps(props, ["size"])

    const emit = defineEmits<{
        change: [checked: boolean]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/check-tag';

    .kel-check-tag {
        &--small {
            --kel-font-size-base: var(--kel-font-size-extra-small);
            padding: 3px 8px;
        }

        &--default {
            --kel-font-size-base: var(--kel-font-size-base);
            padding: 7px 15px;
        }

        &--large {
            --kel-font-size-base: var(--kel-font-size-medium);
            padding: 9px 18px;
        }
    }
</style>
