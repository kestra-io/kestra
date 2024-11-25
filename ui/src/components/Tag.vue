<script setup>
    const props = defineProps({
        icon: {
            type: Object,
            required: true
        },
        label: {
            type: String,
            required: true
        }
    });
</script>

<template>
    <div class="tag">
        <component :is="props.icon" class="icon" />
        <span>{{ props.label }}</span>
    </div>
</template>

<style scoped lang="scss">
    .tag {
        display: inline-flex;
        align-items: center;
        padding: 3px 6px;
        border-radius: 4px;
        border: 1px solid #5BB8FF;
        background-color: #5BB8FF33;
        color: #A2EFFF;

        .icon {
            margin-right: 1px !important;
        }
    }
</style>
