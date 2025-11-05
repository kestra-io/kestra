<template>
    <div class="wrapper">
        <div class="title">
            <slot name="back-button" />
            <h2>{{ title }}</h2>
        </div>
        <div class="content" ref="contentRef">
            <slot />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref} from "vue";

    defineProps<{title:string}>();

    const contentRef = ref<HTMLDivElement | null>(null);

    defineExpose({
        contentRef
    });
</script>

<style scoped lang="scss">
    .wrapper {
        height: 100vh;
        display: flex;
        flex-direction: column;
        background-color: var(--ks-background-panel);
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