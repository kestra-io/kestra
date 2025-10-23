<template>
    <div class="panel-footer">
        <div class="selection-display">
            <small 
                v-if="footerText && timeRangeMode !== 'custom'"
            >
                {{ footerText }}
            </small>
        </div>

        <div class="action-buttons">
            <el-tooltip :content="$t('filter.reset_tooltip')" placement="top" effect="light">
                <el-button size="small" :icon="Restore" @click="emits('reset')" />
            </el-tooltip>
            <el-button
                type="primary"
                size="small"
                @click="emits('apply')"
            >
                {{ $t("filter.footer_apply") }}
            </el-button>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {Restore} from "../../utils/icons";

    defineProps<{
        footerText: string;
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
    bottom: 0;
    flex-shrink: 0;
    position: sticky;
    z-index: 1;
    border-top: 1px solid var(--ks-border-primary);

    .selection-display {
        font-size: 12px;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        margin-right: 0.875rem;
        color: var(--ks-content-tertiary);
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