<template>
    <ElAlert :class="{'is-banner': banner}" v-bind="{...filteredProps(), ...$attrs}">
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
        banner?: boolean
    }>(), {
        showIcon: true,
        banner: false,
    })

    const filteredProps = useFilteredProps(props, ["banner"])

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns' as *;
    @use 'element-plus/theme-chalk/src/alert' as *;
    @use "element-plus/theme-chalk/src/common/var" as *;
</style>

<style>
    .kel-alert {
        --kel-alert-icon-size: 1.5rem;
        --kel-alert-icon-large-size: 1.5rem;
        --kel-alert-description-font-size: var(--ks-font-size-xs);
        --kel-alert-title-with-description-font-size: var(--ks-font-size-sm);

        /* Element Plus centres the row, which leaves the icon mid-block once the message wraps. */
        align-items: flex-start;

        /* Font size of whichever element renders line one; the title shrinks when a description is present. */
        --ks-alert-first-line-font-size: var(--kel-alert-title-font-size);

        &:has(.kel-alert__title.with-description) {
            --ks-alert-first-line-font-size: var(--kel-alert-title-with-description-font-size);
        }

        &:not(:has(.kel-alert__title)) {
            --ks-alert-first-line-font-size: var(--kel-alert-description-font-size);
        }

        & .kel-alert__title {
            line-height: var(--ks-line-height-base);
        }

        & .kel-alert__description {
            line-height: var(--ks-line-height-base);
        }

        & .kel-alert__icon {
            height: var(--kel-alert-icon-size);
            align-self: flex-start;

            & .material-design-icon,
            & .material-design-icon > .material-design-icon__svg {
                height: var(--kel-alert-icon-size);
                width: var(--kel-alert-icon-size);
            }
        }

        &:has(.kel-alert__icon) .kel-alert__content {
            padding-block: max(0px, calc((var(--kel-alert-icon-size) - var(--ks-line-height-base) * var(--ks-alert-first-line-font-size)) / 2));
        }

        & .kel-alert__close-btn {
            top: 50%;
            transform: translateY(-50%);
            color: var(--ks-icon-muted);
        }

        & .kel-alert__title.with-description {
            font-weight: bold;
        }

        &.kel-alert--success.is-light {
            border: 1px solid var(--ks-border-success);
            background-color: var(--ks-bg-success);
            --kel-color-success: var(--ks-text-success);

            &.is-banner {
                border-width: 0 0 1px;
                border-radius: 0;
                --kel-color-success: var(--ks-text-primary);
            }

            & .kel-alert__icon {
                color: var(--ks-icon-success);
            }
        }

        &.kel-alert--info.is-light {
            border: 1px solid var(--ks-border-info);
            background-color: var(--ks-bg-info);
            --kel-color-info: var(--ks-text-info);

            &.is-banner {
                border-width: 0 0 1px;
                border-radius: 0;
                --kel-color-info: var(--ks-text-primary);
            }

            & .kel-alert__icon {
                color: var(--ks-icon-info);
            }
        }

        &.kel-alert--warning.is-light {
            border: 1px solid var(--ks-border-warning);
            background-color: var(--ks-bg-warning);
            --kel-color-warning: var(--ks-text-warning);

            &.is-banner {
                border-width: 0 0 1px;
                border-radius: 0;
                --kel-color-warning: var(--ks-text-primary);
            }

            & .kel-alert__icon {
                color: var(--ks-icon-warning);
            }
        }

        &.kel-alert--error.is-light {
            border: 1px solid var(--ks-border-error);
            background-color: var(--ks-bg-error);
            --kel-color-error: var(--ks-text-error);

            &.is-banner {
                border-width: 0 0 1px;
                border-radius: 0;
                --kel-color-error: var(--ks-text-primary);
            }

            & .kel-alert__icon {
                color: var(--ks-icon-error);
            }
        }
    }
</style>
