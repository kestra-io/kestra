<template>
    <nav class="ks-sidebar" :aria-label="ariaLabel">
        <div v-if="$slots.header" class="ks-sidebar__header">
            <slot name="header" />
        </div>
        <KsScrollbar class="ks-sidebar__body">
            <slot />
        </KsScrollbar>
        <div v-if="$slots.footer" class="ks-sidebar__footer">
            <slot name="footer" />
        </div>
    </nav>
</template>

<script setup lang="ts">
    import KsScrollbar from "../../Basic/KsScrollbar.vue"

    withDefaults(defineProps<{
        ariaLabel?: string
    }>(), {
        ariaLabel: "Sidebar",
    })

    defineSlots<{
        header?(): unknown
        default?(): unknown
        footer?(): unknown
    }>()
</script>

<style scoped lang="scss">
.ks-sidebar {
    display: flex;
    flex-direction: column;
    height: 100%;
    overflow: hidden;
    background-color: var(--ks-bg-sidebar);
    border-right: 1px solid var(--ks-border-default);
    padding: var(--ks-spacing-6) 0 var(--ks-spacing-4);
}

.ks-sidebar__header {
    flex: 0 0 auto;
    padding: 0 var(--ks-spacing-4);
}

.ks-sidebar__body {
    flex: 1 1 auto;
    min-height: 0;
}

.ks-sidebar__footer {
    flex: 0 0 auto;
}
</style>
