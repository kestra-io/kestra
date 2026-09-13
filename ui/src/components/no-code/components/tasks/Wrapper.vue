<template>
    <slot v-if="transparent" name="tasks" />
    <div v-else class="schema-wrapper">
        <slot name="tasks" />
    </div>
</template>

<script setup lang="ts">
    defineOptions({name: "Wrapper"})

    defineProps<{merge?: boolean, transparent?: boolean}>()
</script>

<style scoped lang="scss">
.schema-wrapper {
    margin-left: calc(-1 * var(--ks-spacing-5));
    margin-right: calc(-1 * var(--ks-spacing-5));
    padding: 0 var(--ks-spacing-5) var(--ks-spacing-3);
    margin-bottom: var(--ks-spacing-3);
    border-bottom: 1px solid var(--ks-border-subtle);
}

.schema-wrapper:last-child {
    padding-bottom: 0;
    margin-bottom: 0;
    border-bottom: none;
}
</style>
