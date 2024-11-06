<script lang="ts" setup>
    defineProps<{
        href: string
        title: string
    }>()
</script>

<template>
    <a :href="href" :title="title">
        <slot />
    </a>
</template>

<style scoped>
    a {
        display:block;
        padding: calc(.25 * var(--spacer)) calc(.5 * var(--spacer));
        white-space: nowrap;
        overflow:hidden;
        text-overflow:ellipsis;
        color: var(--el-text-color-regular);
        font-size: 0.875em;
        &:hover{
            color: var(--el-text-color-secondary);
        }
    }
</style>
