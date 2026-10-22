<template>
    <ElTag
        disableTransitions
        v-bind="({...filteredProps(), ...$attrs} as TagProps)"
        :type="(elType as TagProps['type'])"
        :size="elSize"
        :class="{'kel-tag--default': type === undefined, 'kel-tag--error': type === 'error', 'kel-tag--xs': size === 'xs', 'kel-tag--truncate': truncate}"
        @close="emit('close')"
    >
        <template #default>
            <KsIcon v-if="icon || $slots.icon">
                <component :is="icon" v-if="icon" />
                <slot v-else name="icon" />
            </KsIcon>
            <span v-if="label" class="kel-tag__label">{{ label }}</span>
            <slot v-else-if="$slots.default" />
        </template>
    </ElTag>
</template>

<script lang="ts">
    export type KsTagType = "" | "success" | "info" | "warning" | "danger" | "error" | "primary"
</script>

<script setup lang="ts">
    import {ElTag, type TagProps} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import {computed, type Component} from "vue"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        type?: KsTagType
        size?: "large" | "default" | "small" | "xs"
        closable?: boolean
        effect?: "dark" | "light" | "plain"
        icon?: string | Component
        round?: boolean
        label?: string
        plain?: boolean
        /** Clips the label with an ellipsis instead of letting the tag outgrow its container. */
        truncate?: boolean
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

    // ElTag only accepts its five built-in types; "error" is applied via the kel-tag--error class instead
    const elType = computed(() => (props.type === "error" ? undefined : props.type))

    const elSize = computed(() => (props.size === "xs" ? "small" : props.size))

    const filteredProps = useFilteredProps(props, ["icon", "label", "plain", "size", "type", "truncate"])
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/tag';
</style>

<style>
    .kel-tag {
        & .kel-tag__content {
            display: inline-flex;
            align-items: center;
            gap: 4px;
            line-height: 1;
            font-weight: var(--ks-font-weight-semibold);
        }

        /* The close ✕: a muted glyph at rest that firms to full strength on hover. Element Plus's
           default turns it white and fills it with `--kel-tag-hover-color`, which in dark mode reads
           as a solid white dot (kestra-ee#9590); no fill here, so the ✕ stays readable in every theme. */

        & .kel-tag__close {
            color: var(--ks-icon-muted);

            &:hover {
                color: var(--ks-icon-default) !important;
                background-color: transparent !important;
            }
        }

        &.kel-tag--xs {
            --kel-tag-font-size: var(--ks-font-size-xs);
            height: var(--ks-spacing-4);
            padding: 0 var(--ks-spacing-1);

            & .kel-tag__content {
                font-weight: var(--ks-font-weight-medium);
            }
        }

        & [class*="kel-icon"] {
            display: inline-flex;
            align-items: center;
            line-height: 0;

            & .material-design-icon,
            & .material-design-icon__svg {
                width: 1em;
                height: 1em;
            }

            & .material-design-icon__svg {
                position: static;
            }
        }

        &.kel-tag--default.kel-tag--plain [class*="kel-icon"] .material-design-icon {
            color: var(--ks-icon-muted);
        }

        &.kel-tag--truncate {
            max-width: 100%;

            & .kel-tag__content {
                min-width: 0;
            }

            & .kel-tag__label,
            & .kel-tag__content > span {
                min-width: 0;
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }

        &.kel-tag--plain {
            --kel-tag-bg-color: var(--ks-bg-tag);
            --kel-tag-text-color: var(--ks-bg-tag);
            --kel-tag-border-color: var(--ks-bg-tag);
            --kel-tag-hover-color: var(--ks-bg-tag);
        }

        &.kel-tag--plain.kel-tag--primary {
            --kel-tag-bg-color: var(--ks-status-background-paused);
            --kel-tag-text-color: var(--ks-status-paused);
            --kel-tag-border-color: var(--ks-status-border-paused);
            --kel-tag-hover-color: var(--ks-status-paused);

            & a {
                color: var(--ks-status-paused);
            }
        }

        &.kel-tag--plain.kel-tag--success {
            --kel-tag-bg-color: var(--ks-bg-success);
            --kel-tag-text-color: var(--ks-status-success);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-status-success);

            & a {
                color: var(--ks-status-success);
            }
        }

        &.kel-tag--plain.kel-tag--warning {
            --kel-tag-bg-color: var(--ks-bg-warning);
            --kel-tag-text-color: var(--ks-status-warning);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-status-warning);

            & a {
                color: var(--ks-status-warning);
            }
        }

        &.kel-tag--plain.kel-tag--danger {
            --kel-tag-bg-color: var(--ks-bg-error);
            --kel-tag-text-color: var(--ks-status-error);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-status-error);

            & a {
                color: var(--ks-status-error);
            }
        }

        &.kel-tag--plain.kel-tag--error {
            --kel-tag-bg-color: var(--ks-bg-error);
            --kel-tag-text-color: var(--ks-text-error);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-text-error);

            & a {
                color: var(--ks-text-error);
            }
        }

        &.kel-tag--plain.kel-tag--info {
            --kel-tag-bg-color: var(--ks-bg-info);
            --kel-tag-text-color: var(--ks-status-info);
            --kel-tag-border-color: transparent;
            --kel-tag-hover-color: var(--ks-status-info);

            & a {
                color: var(--ks-status-info);
            }
        }

        &.kel-tag--default {
            color: var(--ks-text-secondary);

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

                & a {
                    color: var(--ks-text-primary);
                }
            }
        }
    }
</style>
