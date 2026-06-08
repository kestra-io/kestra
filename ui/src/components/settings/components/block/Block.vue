<template>
    <section class="settings-block">
        <KsText tag="h2" class="settings-block__heading">{{ heading }}</KsText>
        <KsCard shadow="never" :bodyStyle="{padding: '0'}">
            <slot />
        </KsCard>
    </section>
</template>

<script setup lang="ts">
    defineProps<{
        heading: string
    }>()
</script>

<style scoped lang="scss">
    .settings-block {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-4);

        &__heading {
            align-self: flex-start;
            font-size: var(--ks-font-size-2xl);
            font-weight: 600;
            color: var(--ks-text-primary);
        }
    }
</style>
