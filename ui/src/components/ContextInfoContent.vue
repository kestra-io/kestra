<template>
    <div class="wrapper">
        <div class="title">
            <slot name="back-button" />
            <h2>{{ title }}</h2>
        </div>
        <div class="content">
            <slot />
        </div>
    </div>
</template>

<script lang="ts" setup>
    defineProps<{title:string}>();
</script>

<style lang="scss" scoped>
    .wrapper {
        height: 100vh;
        display: flex;
        flex-direction: column;
        .content {
            overflow-y: auto;
        }
    }

    .title {
        display: flex;
        padding: 1rem;
        padding-left: 1.6rem;
        padding-right: 3rem;
        border-bottom: 1px solid var(--ks-border-primary);
        align-items: center;
        gap: 1rem;

        h2 {
            font-size: var(--font-size-lg);
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
            margin-bottom: 0;
            margin-top: 0;
            width: 100%;
            line-height: 1.2;
        }
    }
</style>