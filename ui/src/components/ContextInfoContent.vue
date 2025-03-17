<template>
    <div class="wrapper">
        <div class="title">
            <h2>{{ props.title }}</h2>
        </div>
        <div class="content">
            <slot />
        </div>
    </div>
</template>

<script setup>
    const props = defineProps({
        title: {
            type: String,
            required: true
        },
    });
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
        padding-right: 3rem;
        border-bottom: 1px solid var(--ks-border-primary);

        h2 {
            font-size: var(--font-size-lg);
            white-space: nowrap;
            text-overflow: ellipsis;
            overflow: hidden;
            margin-bottom: 0;
            width: 100%;
        }
    }
</style>
