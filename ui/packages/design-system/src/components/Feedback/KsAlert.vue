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

<script lang="ts">
    export const KS_ALERT_TYPES = ["success", "warning", "info", "error"] as const
    export type KsAlertType = typeof KS_ALERT_TYPES[number]
</script>
<script setup lang="ts">
    import {computed} from "vue"
    import {ElAlert} from "element-plus"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import AlertBoxOutline from "vue-material-design-icons/AlertBoxOutline.vue"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        type?: KsAlertType
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

    // Element Plus renders an untyped alert as info. KsAlert.test.ts checks these tokens exist.
    const alertType = computed(() => props.type ?? "info")
    const borderColor = computed(() => `var(--ks-border-${alertType.value})`)
    const backgroundColor = computed(() => `var(--ks-bg-${alertType.value})`)
    const textColor = computed(() => `var(--ks-text-${alertType.value})`)
    const iconColor = computed(() => `var(--ks-icon-${alertType.value})`)

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

        &.is-light {
            border: 1px solid v-bind(borderColor);
            background-color: v-bind(backgroundColor);
            color: v-bind(textColor);

            & .kel-alert__description {
                color: inherit;
            }

            & .kel-alert__icon {
                color: v-bind(iconColor);
            }

            &.is-banner {
                border-width: 0 0 1px;
                border-radius: 0;
                color: var(--ks-text-primary);
            }
        }
    }
</style>
