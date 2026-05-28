<template>
    <KsCol
        :xs="layout.xs"
        :sm="layout.sm"
        :md="layout.md"
        :lg="layout.lg"
        :xl="layout.xl"
        class="column"
    >
        <p v-if="label" v-text="label" class="label" />
        <slot />
    </KsCol>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    const props = defineProps({
        overrides: {type: Object, default: () => {}},
        label: {type: String, default: undefined},
    })

    const layout = computed(() => {
        return {
            xs: props.overrides?.xs || 24,
            sm: props.overrides?.sm || 12,
            md: props.overrides?.md || 12,
            lg: props.overrides?.lg || 8,
            xl: props.overrides?.xl || 6,
        }
    })
</script>

<style scoped lang="scss">

.column {
    margin-bottom: 1rem;

    & p.label {
        margin-bottom: calc(1rem / 3);
        font-size: var(--ks-font-size-sm);
        font-weight: 500;
    }
}
</style>
