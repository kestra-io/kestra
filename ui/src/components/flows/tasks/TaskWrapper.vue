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
    border: 1px solid var(--ks-border-primary);
    padding: 1rem;
    padding-bottom: 0;
    border-radius: 4px;
    margin: 1rem 0;
    background: var(--ks-background-body);
}
</style>
