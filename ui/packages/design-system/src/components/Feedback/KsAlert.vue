<template>
    <ElAlert v-bind="({...filteredProps(), showIcon: resolvedShowIcon, ...$attrs} as any)">
        <template v-if="resolvedShowIcon" #icon>
            <slot name="icon">
                <component :is="TYPE_ICONS[type ?? 'info']" />
            </slot>
        </template>
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
    </ElAlert>
</template>

<script setup lang="ts">
    import {computed, useSlots} from "vue"
    import {ElAlert} from "element-plus"
    import {TYPE_ICONS, type FeedbackType} from "./typeIcons"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        type?: FeedbackType
        title?: string
        description?: string
        closable?: boolean
        showIcon?: boolean
        center?: boolean
        effect?: "light" | "dark"
    }>()

    const slots = useSlots()
    const resolvedShowIcon = computed(() => props.showIcon || !!slots.icon)

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        icon?(): unknown
        default?(): unknown
        title?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns' as *;
    @use 'element-plus/theme-chalk/src/alert' as *;
    @use "element-plus/theme-chalk/src/common/var" as *;

    .kel-alert {
        --kel-alert-icon-size: 1.5rem;
        --kel-alert-icon-large-size: 1.5rem;
        --kel-alert-title-font-size: var(--ks-font-size-sm);
        --kel-alert-title-with-description-font-size: var(--ks-font-size-sm);
        --kel-alert-description-font-size: var(--ks-font-size-2xs);
        --kel-alert-padding: 0.75rem 1rem;

        box-shadow: 0 1px 4px 0 var(--ks-shadow-element);

        .kel-alert__title {
            font-weight: 600;
            line-height: 1;
        }

        .kel-alert__description {
            font-weight: 500;
            line-height: 1.25;
        }

        .kel-alert__icon {
            .material-design-icon,
            .material-design-icon > .material-design-icon__svg {
                height: var(--kel-alert-icon-size);
                width: var(--kel-alert-icon-size);
            }
        }

        .kel-alert__close-btn {
            top: 50%;
            transform: translateY(-50%);
            color: var(--ks-icon-muted);
        }

        @each $type in (success, info, warning, error) {
            &.kel-alert--#{$type}.is-light {
                border: 1px solid var(--ks-border-#{$type});
                background-color: var(--ks-bg-#{$type});
                #{--kel-color-#{$type}}: var(--ks-text-#{$type});

                .kel-alert__icon {
                    color: var(--ks-icon-#{$type});
                }
            }
        }
    }
</style>
