<template>
    <el-alert id="error" type="error" showIcon :closable="false">
        <template #title>
            <div v-if="logs.length" @click="isExpanded = !isExpanded">
                <Markdown
                    v-if="logs.at(-1)?.message"
                    :source="`${$t('execution_failed')}: ${logs.at(-1)!.message}`"
                    :html="false"
                />
                <component :is="isExpanded ? ChevronUp : ChevronDown" />
            </div>
            <span v-else>{{ $t("error detected") }}</span>
        </template>

        <div v-if="isExpanded && logs" class="logs">
            <div v-for="(log, lIdx) in logs.slice(0, 4)" :key="lIdx">
                <LogLine
                    :level="log.level"
                    :log="log"
                    :excludeMetas="['namespace', 'flowId', 'executionId']"
                />
            </div>
            <div v-if="logs.length > 3" class="link">
                <router-link :to>
                    <el-button>
                        {{ $t("errorLogs") }}
                    </el-button>
                </router-link>
            </div>
        </div>
    </el-alert>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";

    import {
        Execution,
        useExecutionsStore,
    } from "../../../../../stores/executions";
    const store = useExecutionsStore();

    import {Log} from "../../../../../stores/logs";

    import Markdown from "../../../../layout/Markdown.vue";
    import LogLine from "../../../../logs/LogLine.vue";

    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";

    const props = defineProps<{ execution: Execution }>();

    const isExpanded = ref(false);

    const to = {
        name: "executions/update",
        params: {
            tenantId: props.execution.tenantId,
            id: props.execution.id,
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
            tab: "logs",
        },
        query: {level: "ERROR"},
    };

    const logs = ref<Log[]>([]);

    onMounted(async () => {
        const response = await store.loadLogs({
            store: false,
            executionId: props.execution.id,
            params: {minLevel: "ERROR"},
        });

        if (!response.length) return;

        logs.value = response;
    });
</script>
<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

#error {
    :deep(.el-alert__content) {
        cursor: pointer;
        width: 100%;
        max-width: 100%;
        gap: 0;

        & .el-alert__title {
            & div,
            & span {
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: var(--el-alert-title-font-size);
                line-height: 24px;
                color: var(--el-color-error);

                & .markdown p {
                    margin-bottom: 0;
                }
            }
        }

        & .el-alert__description {
            color: var(--ks-content-primary);

            & .logs {
                padding-top: calc($spacer * 1.5);

                > div {
                    width: 100%;

                    & .line {
                        & .header {
                            display: flex;
                            flex-wrap: wrap;
                            margin-bottom: calc($spacer / 2);

                            & span {
                                margin-left: 0;
                            }
                        }
                    }
                }

                .el-button {
                    color: var(--ks-log-content-error);
                }

                .link {
                    padding: $spacer 0 calc($spacer / 2) 0;
                    border-top: 1px solid var(--ks-border-primary);
                    text-align: right;
                }
            }
        }
    }
}
</style>
