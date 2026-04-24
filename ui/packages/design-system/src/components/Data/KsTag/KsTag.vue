<template>
    <ElTag
        disableTransitions
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @close="emit('close')"
    >
        <template #default>
            <KsIcon v-if="icon || $slots.icon">
                <component :is="icon" v-if="icon" />
                <slot v-else name="icon" />
            </KsIcon>
            <span v-if="label">{{ label }}</span>
            <span v-else-if="$slots.default"><slot /></span>
        </template>
    </ElTag>
</template>

<script setup lang="ts">
    import {ElTag} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import type {Component} from "vue";

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "success" | "info" | "warning" | "danger" | "primary"
        size?: "large" | "default" | "small"
        closable?: boolean
        effect?: "dark" | "light" | "plain"
        icon?: string | Component
        round?: boolean
        label?: string
    }>()

    const emit = defineEmits<{
        close: []
    }>()

    defineSlots<{
        default?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["icon", "label"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tag';
    @use "element-plus/theme-chalk/src/common/var.scss" as *;
    @use "../../../assets/styles/variables.scss" as *;

    .kel-tag {
        .kel-tag__content {
            display: inline-flex;
            align-items: center;
        }

        & [class*=kel-icon] + span {
            margin-left: 6px;
        }

        @each $i in ($types) {
            &.kel-tag--#{$i} a {
                color: var(--kel-color-#{$i}-dark-2);
            }
        }

        &.kel-tag--plain {
            @each $i in ($types) {
                &.kel-tag--#{$i} {
                    --kel-tag-bg-color: transparent;
                    --kel-tag-text-color: var(--kel-color-#{$i}-dark-2);
                    --kel-tag-border-color: var(--kel-color-#{$i}-dark-2);
                }
            }
        }
    }
</style>
