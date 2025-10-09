<template>
    <div class="panel-footer">
        <div class="selection-display">
            <small v-if="footerDisplayText && timeRangeMode !== 'custom'">{{ footerDisplayText }}</small>
        </div>

        <div class="action-buttons">
            <el-button size="small" :icon="RefreshButton" @click="emits('reset')" />
            <el-button
                type="primary"
                size="small"
                @click="emits('apply')"
            >
                Apply
            </el-button>
        </div>
    </div>
</template>

<script setup lang="ts">
    import RefreshButton from "vue-material-design-icons/Refresh.vue";

    defineProps<{
        footerDisplayText: string;
        timeRangeMode: "predefined" | "custom";
    }>();

    const emits = defineEmits<{
        reset: [];
        apply: [];
    }>();
</script>

<style lang="scss" scoped>
.panel-footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 1rem;
    border-top: 1px solid var(--ks-border-primary);
    bottom: 0;
    flex-shrink: 0;
    position: sticky;
    z-index: 1;

    .selection-display {
        color: var(--ks-content-tertiary);
        font-size: 12px;
    }

    .action-buttons {
        display: flex;
        gap: 0.5rem;

        :deep(.el-button) {
            margin: 0;
        }
    }
}
</style>