<template>
    <ElEmpty v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template #description>
            <slot name="description">
                <!-- eslint-disable-next-line vue/no-v-html -->
                <span v-html="description ?? t('no_data').replaceAll('\n', '<br >')" />
            </slot>
        </template>
        <template v-if="$slots.image" #image>
            <slot name="image" />
        </template>
    </ElEmpty>
</template>

<script setup lang="ts">
    import {ElEmpty, provideGlobalConfig} from "element-plus"
    import {useI18n} from "vue-i18n"
    import {useFilteredProps} from "../../utils/filteredProps"
    import noDataImage from "../../assets/images/no-data.png"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        image?: string
        imageSize?: number
        description?: string
    }>(), {
        image: noDataImage,
        imageSize: 180,
        description: undefined,
    })

    const slots = defineSlots<{
        default?(): unknown
        description?(): unknown
        image?(): unknown
    }>()

    const filteredProps = useFilteredProps(props, slots.image ? ["image", "description"] : ["description"])

    const {t} = useI18n({useScope: "global"})
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/empty';

    .kel-empty {
        background-color: var(--ks-background-card);
    }

    .kel-empty__description {
        font-size: var(--kel-font-size-small);
        color: var(--ks-content-secondary);
    }
</style>
