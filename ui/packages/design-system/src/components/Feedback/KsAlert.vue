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
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"
    import InformationSlabCircleOutline from "vue-material-design-icons/InformationSlabCircleOutline.vue"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const TYPE_ICONS = {
        success: CheckCircleOutline,
        info: InformationSlabCircleOutline,
        error: AlertBoxOutline,
        warning: AlertOutline,
    } as const

    const props = defineProps<{
        type?: "success" | "warning" | "info" | "error"
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
    $window-close-svg: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Cpath d='M13.46,12L19,17.54V19H17.54L12,13.46L6.46,19H5V17.54L10.54,12L5,6.46V5H6.46L12,10.54L17.54,5H19V6.46L13.46,12Z'/%3E%3C/svg%3E");

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
        --kel-alert-close-font-size: 1rem;

        .kel-alert__title {
            font-weight: 600;
            line-height: 0.875rem;
        }

        .kel-alert__description {
            font-weight: 500;
            line-height: 0.875rem;
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

            svg { display: none; }

            &::before {
                content: '';
                display: block;
                width: 1rem;
                height: 1rem;
                background-color: currentColor;
                mask-image: $window-close-svg;
                mask-size: contain;
                mask-repeat: no-repeat;
            }
        }

        @each $type in $types {
            &.kel-alert--#{$type}.is-light {
                border: 1px solid var(--ks-border-#{$type});
                background-color: var(--ks-bg-#{$type});
                #{--kel-color-#{$type}}: var(--ks-text-#{$type});
            }
        }
    }
</style>
