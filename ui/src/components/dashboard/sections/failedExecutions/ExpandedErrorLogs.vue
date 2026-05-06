<template>
    <div v-ks-loading="loading" class="expanded-error-logs">
        <KsAlert
            v-if="error"
            type="error"
            showIcon
            :closable="false"
            :title="$t('dashboards.flow_overview.errors_load_failed')"
        />

        <p v-else-if="!loading && !logs.length" class="empty">
            {{ $t("dashboards.flow_overview.errors_in_execution_empty") }}
        </p>

        <div v-else-if="!loading" class="logs">
            <LogLine
                v-for="(log, index) in logs"
                :key="`${log.timestamp}-${index}`"
                :level="log.level"
                :log="log"
                :excludeMetas="['namespace', 'flowId', 'executionId']"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue";

    import {KsAlert, vKsLoading} from "@kestra-io/design-system";

    import LogLine from "../../../logs/LogLine.vue";
    import {useExecutionsStore} from "../../../../stores/executions";
    import type {Log} from "../../../../stores/logs";

    const props = defineProps<{
        executionId?: string;
    }>();

    const store = useExecutionsStore();

    const logs = ref<Log[]>([]);
    const loading = ref(false);
    const error = ref(false);

    const fetchLogs = async (executionId: string) => {
        loading.value = true;
        error.value = false;
        try {
            const response = await store.loadLogs({
                executionId,
                params: {minLevel: "ERROR"},
                store: false,
            });
            logs.value = Array.isArray(response) ? response : [];
        } catch {
            error.value = true;
            logs.value = [];
        } finally {
            loading.value = false;
        }
    };

    watch(
        () => props.executionId,
        (executionId) => {
            if (executionId) {
                fetchLogs(executionId);
            } else {
                logs.value = [];
                loading.value = false;
                error.value = false;
            }
        },
        {immediate: true},
    );
</script>

<style lang="scss" scoped>
    .expanded-error-logs {
        padding: 0.5rem 1rem;
        background: var(--ks-background-base);

        .empty {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            color: var(--ks-content-tertiary);
        }

        .logs {
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
        }
    }
</style>
