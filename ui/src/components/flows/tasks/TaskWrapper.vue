<template>
    <div class="schema-wrapper">
        <slot name="tasks" />
    </div>
</template>

<script setup>
    defineOptions({name: "TaskWrapper"});
</script>

<style lang= "scss" scoped>
.schema-wrapper {
    border: 1px solid var(--ks-border-secondary);
    padding: 1rem;
    padding-bottom: .20rem;
    border-radius: 8px;
    margin: 1rem 0;
    background: var(--ks-background-body);
    box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
}
</style>
