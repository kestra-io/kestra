<template>
    <ElAlert v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>

        <template #icon>
            <CheckCircleOutline v-if="type === 'success'" />
            <InformationOutline v-if="type === 'info'" />
            <AlertCircleOutline v-if="type === 'warning'" />
            <AlertBoxOutline v-if="type === 'error'" />
        </template>
    </ElAlert>
</template>

<script setup lang="ts">
    import {ElAlert} from "element-plus"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        type?: "success" | "warning" | "info" | "error"
        title?: string
        description?: string
        closable?: boolean
        showIcon?: boolean
        center?: boolean
    }>(), {
        showIcon: true,
    })

    const filteredProps = useFilteredProps(props)

    defineSlots<{
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
        --kel-alert-description-font-size: var(--ks-font-size-xs);
        --kel-alert-title-with-description-font-size: var(--ks-font-size-sm);

         .kel-alert__title {
            line-height: 1;
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

        .kel-alert__title.with-description {
            font-weight: bold;
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
