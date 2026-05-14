<template>
    <ElTag
        disableTransitions
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="{'kel-tag--neutral': plain}"
        @close="emit('close')"
    >
        <template #default>
            <KsIcon v-if="icon || $slots.icon">
                <component :is="icon" v-if="icon" />
                <slot v-else name="icon" />
            </KsIcon>
            <template v-if="label">
                {{ label }}
            </template>
            <slot v-else-if="$slots.default" />
        </template>
    </ElTag>
</template>

<script setup lang="ts">
    import {ElTag} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import type {Component} from "vue"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: "" | "success" | "info" | "warning" | "danger" | "primary"
        size?: "large" | "default" | "small"
        closable?: boolean
        effect?: "dark" | "light" | "plain"
        icon?: string | Component
        round?: boolean
        label?: string
        plain?: boolean
    }>()

    const emit = defineEmits<{
        close: []
    }>()

    defineSlots<{
        default?(): unknown
        icon?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, ["icon", "label", "plain"])
</script>

<style lang="scss">
    @use "sass:map";
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tag';
    @use "element-plus/theme-chalk/src/common/var.scss" as *;
    @use "../../../assets/styles/variables.scss" as *;

    $tag-color-map: (
        primary: (
            bg: var(--ks-button-background-primary),
            border: var(--ks-button-background-primary),
            text: var(--ks-white),
        ),
        success: (
            bg: var(--ks-background-success),
            border: var(--ks-border-success),
            text: var(--ks-content-success),
        ),
        warning: (
            bg: var(--ks-log-background-warn),
            border: var(--ks-log-border-warn),
            text: var(--ks-log-content-warn),
        ),
        danger: (
            bg: var(--ks-log-background-error),
            border: var(--ks-log-border-error),
            text: var(--ks-log-content-error),
        ),
        error: (
            bg: var(--ks-log-background-error),
            border: var(--ks-log-border-error),
            text: var(--ks-log-content-error),
        ),
        info: (
            bg: var(--ks-badge-background),
            border: var(--ks-badge-border),
            text: var(--ks-badge-content),
        ),
    );

    .kel-tag {
        .kel-tag__content {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            line-height: 1;
        }

        [class*="kel-icon"] {
            display: inline-flex;
            align-items: center;
            line-height: 0;
        }

        @each $i, $colors in $tag-color-map {
            &.kel-tag--#{$i} {
                --kel-tag-bg-color: #{map.get($colors, bg)};
                --kel-tag-text-color: #{map.get($colors, text)};
                --kel-tag-hover-color: #{map.get($colors, text)};

                a {
                    color: #{map.get($colors, text)};
                }
            }
        }

        &.kel-tag--neutral {
            --kel-tag-bg-color: #ECEBEF;
            --kel-tag-text-color: var(--ks-content-primary);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-content-primary);

            a {
                color: var(--ks-content-primary);
            }
        }
    }

    html.dark .kel-tag.kel-tag--neutral {
        --kel-tag-bg-color: #5A6079;
    }
</style>
