<template>
    <div :class="{'schema-wrapper': true, bordered: !merge}">
        <slot name="tasks" />
    </div>
</template>

<script setup>
    defineOptions({name: "TaskWrapper"});

    defineProps({merge: {type: Boolean, default: false}});
</script>

<style lang="scss" scoped>
.schema-wrapper {
    padding: 1rem;
    padding-bottom: 0.2rem;
    border-radius: 8px;
    margin: 1rem 0;
    background: var(--ks-background-body);
}
.bordered {
    border: 1px solid var(--ks-border-secondary);
    box-shadow: 0 0 0 1px var(--ks-border-primary) inset;
}
</style>
