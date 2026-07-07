<template>
    <ElTag
        disableTransitions
        v-bind="({...filteredProps(), ...$attrs} as any)"
        :class="{'kel-tag--default': type === undefined}"
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
    import {type Component} from "vue"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        type?: "" | "success" | "info" | "warning" | "danger" | "primary"
        size?: "large" | "default" | "small"
        closable?: boolean
        effect?: "dark" | "light" | "plain"
        icon?: string | Component
        round?: boolean
        label?: string
        plain?: boolean
    }>(), {
        effect: "plain",
    })

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
            bg: var(--ks-status-background-paused),
            border: var(--ks-status-border-paused),
            text: var(--ks-status-paused),
        ),
        success: (
            bg: var(--ks-status-background-success),
            border: var(--ks-status-border-success),
            text: var(--ks-status-success),
        ),
        warning: (
            bg: var(--ks-log-background-warn),
            border: var(--ks-log-border-warn),
            text: var(--ks-status-warn),
        ),
        danger: (
            bg: #FF6A6C1A,
            border: var(--ks-log-border-error),
            text: var(--ks-status-error),
        ),
        error: (
            bg: var(--ks-log-background-error),
            border: var(--ks-log-border-error),
            text: var(--ks-status-error),
        ),
        info: (
            bg: #718BFE1A,
            border: transparent,
            text: var(--ks-status-info),
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

            .material-design-icon,
            .material-design-icon__svg {
                width: 1em;
                height: 1em;
            }

            .material-design-icon__svg {
                position: static;
            }
        }

        &.kel-tag--default.kel-tag--plain [class*="kel-icon"] .material-design-icon {
            color: var(--ks-icon-muted);
        }

        &.kel-tag--plain {
            --kel-tag-bg-color: var(--ks-bg-tag);
            --kel-tag-text-color: var(--ks-bg-tag);
            --kel-tag-border-color: var(--ks-bg-tag);
            --kel-tag-hover-color: var(--ks-bg-tag);
        }

        @each $i, $colors in $tag-color-map {
            &.kel-tag--plain.kel-tag--#{$i} {
                --kel-tag-bg-color: #{map.get($colors, bg)};
                --kel-tag-text-color: #{map.get($colors, text)};
                --kel-tag-border-color: #{map.get($colors, border)};
                --kel-tag-hover-color: #{map.get($colors, text)};

                a {
                    color: #{map.get($colors, text)};
                }
            }
        }

        &.kel-tag--default {
            //--kel-tag-bg-color: var(--ks-bg-tag);
            //--kel-tag-text-color: var(--ks-text-primary);
            //--kel-tag-border-color: var(--ks-border-default);
            //--kel-tag-hover-color: var(--ks-text-primary);
            //
            //a {
            //    color: var(--ks-text-primary);
            //}

            &.kel-tag--dark {
                --kel-tag-bg-color: var(--ks-gray-cool-500);
                --kel-tag-border-color: var(--ks-border-default);
            }

            &.kel-tag--light {
                --kel-tag-text-color: var(--ks-black);
                --kel-tag-bg-color: var(--ks-gray-cool-50);

            }

            &.kel-tag--plain {
                --kel-tag-bg-color: var(--ks-bg-tag);
                --kel-tag-text-color: var(--ks-text-primary);
                --kel-tag-border-color: transparent;
                --kel-tag-hover-color: var(--ks-text-primary);

                a {
                    color: var(--ks-text-primary);
                }
            }
        }
    }
</style>
