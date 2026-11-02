<template>
    <ElTag
        disableTransitions
        v-bind="({...filteredProps(), ...$attrs} as TagProps)"
        :type="(elType as TagProps['type'])"
        :size="elSize"
        :class="{'kel-tag--default': type === undefined, 'kel-tag--error': type === 'error', 'kel-tag--xs': size === 'xs', 'kel-tag--truncate': truncate, 'kel-tag--toned': tone}"
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

    interface KsTagTone {
        bg: string
        border: string
        text: string
    }

    export const KS_TAG_TONES: Partial<Record<KsTagType, KsTagTone>> = {
        primary: {bg: "var(--ks-status-background-paused)", border: "var(--ks-status-border-paused)", text: "var(--ks-status-paused)"},
        success: {bg: "var(--ks-bg-success)", border: "transparent", text: "var(--ks-status-success)"},
        warning: {bg: "var(--ks-bg-warning)", border: "transparent", text: "var(--ks-status-warning)"},
        danger: {bg: "var(--ks-bg-error)", border: "transparent", text: "var(--ks-status-error)"},
        error: {bg: "var(--ks-bg-error)", border: "transparent", text: "var(--ks-text-error)"},
        info: {bg: "var(--ks-bg-info)", border: "transparent", text: "var(--ks-status-info)"},
    }
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

    const tone = computed(() => (props.type ? KS_TAG_TONES[props.type] : undefined))

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

        &.kel-tag--plain.kel-tag--toned {
            --kel-tag-bg-color: v-bind("tone?.bg");
            --kel-tag-text-color: v-bind("tone?.text");
            --kel-tag-border-color: v-bind("tone?.border");
            --kel-tag-hover-color: v-bind("tone?.text");

            & a {
                color: v-bind("tone?.text");
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
