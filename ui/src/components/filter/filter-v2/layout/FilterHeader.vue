<template>
    <div class="filter-header">
        <label class="filter-label">{{ label }}</label>
        <el-button
            type="text"
            size="small"
            :icon="Close"
            class="close-icon"
            @click="emits('close')"
        />
    </div>
</template>

<script setup lang="ts">
    import Close from "vue-material-design-icons/Close.vue";

    defineProps<{
        label: string;
    }>();

    const emits = defineEmits<{
        close: [];
    }>();
</script>

<style lang="scss" scoped>
    .filter-header {
        padding: 1rem 1rem 0.25rem 1rem;
        display: flex;
        align-items: center;
        justify-content: space-between;

        .filter-label {
            font-size: 16px;
            font-weight: 700;
            color: var(--ks-content-primary);
            margin: 0;
        }

        .close-icon {
            color: var(--ks-content-tertiary);
            font-size: 16px;
            cursor: pointer;
        }
    }
</style>