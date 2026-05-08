<template>
    <ElCard v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.header" #header>
            <slot name="header" />
        </template>
        <template v-if="$slots.footer" #footer>
            <slot name="footer" />
        </template>
    </ElCard>
</template>

<script setup lang="ts">
    import {ElCard} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        shadow?: "always" | "hover" | "never"
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        header?(): unknown
        footer?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/card';

    .kel-card {
        border-radius: var(--kel-border-radius-round);
        --kel-card-border-color: var(--ks-border-default);
        --kel-card-border-radius: var(--kel-border-radius-round);
        --kel-card-padding: 1rem;
        color: var(--ks-text-primary);
        background-color: var(--ks-bg-surface);

        .kel-card__header {
            padding: 0.5rem 1rem;
            font-weight: bold;
        }
    }
</style>
