<template>
    <el-table stripe tableLayout="auto" fixed :data="Object.entries(data).map(([key, value]) => ({key, value}))">
        <el-table-column prop="key" rowspan="3" :label="$t('name')">
            <template #default="scope">
                <code>{{ scope.row.key }}</code>
            </template>
        </el-table-column>

        <el-table-column prop="value" :label="$t('value')">
            <template #default="scope">
                <template v-if="scope.row.key === 'description'">
                    <Markdown :source="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'cron'">
                    <Cron :cronExpression="scope.row.value" />
                </template>
                <template v-else-if="scope.row.key === 'key'">
                    {{ scope.row.value }}
                    <el-button @click="emit('on-copy', null)">
                        {{ $t('copy url') }}
                    </el-button>
                </template>
                <template v-else>
                    <VarValue :value="scope.row.value" :execution="execution" :restrictUri="true" />
                </template>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup lang="ts">
    import VarValue from "../executions/VarValue.vue";
    import Markdown from "../layout/Markdown.vue";
    import Cron from "../layout/Cron.vue";

    defineProps<{
        data: Record<string, any>;
        execution?: Record<string, any>;
    }>();
    const emit = defineEmits<{ (e: "on-copy", event: any): void }>();
</script>

<style lang="scss" scoped>
    :deep(.markdown) {
        p {
            margin-bottom: auto;
        }
    }

    :deep(.el-table__cell:nth-child(2) span) {
        color: var(--ks-content-secondary);
    }
</style>