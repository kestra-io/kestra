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
            <AlertOutline v-if="type === 'error'" />
        </template>
    </ElAlert>
</template>

<script setup lang="ts">
    import {ElAlert} from "element-plus"
    import CheckCircleOutline from "vue-material-design-icons/CheckCircleOutline.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import AlertCircleOutline from "vue-material-design-icons/AlertCircleOutline.vue"
    import AlertOutline from "vue-material-design-icons/AlertOutline.vue"

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
        --kel-alert-description-font-size: var(--ks-font-size-sm);
        --kel-alert-title-with-description-font-size: var(--ks-font-size-sm);

        .kel-alert__title.with-description {
            font-weight: bold;
        }

        &.kel-alert--success.is-light {
            border: 1px solid var(--ks-border-success);
            background-color: var(--ks-bg-success);
            --kel-color-success: var(--ks-text-success);
        }

        &.kel-alert--warning.is-light {
            border: 1px solid var(--ks-border-warning);
            background-color: var(--ks-bg-warning);
            --kel-color-warning: var(--ks-text-warning);
        }

        &.kel-alert--info.is-light {
            border: 1px solid var(--ks-border-info);
            background-color: var(--ks-bg-info);
            --kel-color-info: var(--ks-text-info);
        }

        &.kel-alert--error.is-light {
            border: 1px solid var(--ks-border-error);
            background-color: var(--ks-bg-error);
            --kel-color-error: var(--ks-text-error);
        }
    }
</style>
