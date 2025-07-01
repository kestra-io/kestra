<template>
    <div class="schema-wrapper" :class="{bordered: !merge}">
        <slot name="tasks" />
    </div>
</template>

<script lang="ts" setup>
    defineOptions({name: "TaskWrapper"});

    defineProps<{merge?: boolean}>();
</script>

<style lang="scss" scoped>
.schema-wrapper {
    width: 100%;
    padding-bottom: 1rem;
    border-radius: 8px;
}
.bordered {
    background: var(--ks-background-box);
    border: 1px solid var(--ks-border-secondary);
    box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
    margin: 1rem 0;
    padding: 1rem;
}
</style>
