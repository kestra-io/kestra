<template>
    <KsAlert id="error" type="error" :closable="false">
        <template #title>
            <span v-if="logs.at(-1)?.message">{{ $t('execution_failed') }}:</span>
        </template>

        <div v-if="logs" class="logs">
            <div v-for="(log, lIdx) in logs.slice(0, 4)" :key="lIdx">
                <LogLine
                    :level="log.level"
                    :log="{...log, message: stripBackticks(log.message ?? '')}"
                    :excludeMetas="['namespace', 'flowId', 'executionId']"
                />
            </div>
            <div v-if="logs.length > 3" class="link">
                <router-link :to>
                    <KsButton>
                        {{ $t("errorLogs") }}
                    </KsButton>
                </router-link>
            </div>
        </div>
    </KsAlert>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue"

    import {
        Execution,
        useExecutionsStore,
    } from "../../../../../stores/executions"
    const store = useExecutionsStore()

    import {Log} from "../../../../../stores/logs"

    import LogLine from "../../../../logs/LogLine.vue"

    const props = defineProps<{ execution: Execution }>()

    function stripBackticks(message: string): string {
        return message.replace(/`([^`]*)`/g, "$1")
    }

    const to = {
        name: "executions/update",
        params: {
            tenantId: props.execution.tenantId,
            id: props.execution.id,
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
            tab: "logs",
        },
        query: {"filters[level][EQUALS]": "ERROR"},
    }

    const logs = ref<Log[]>([])

    onMounted(async () => {
        try {
            const response = await store.loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {minLevel: "ERROR"},
                showMessageOnError: false,
            })

            if (!response.length) return

            logs.value = response
        } catch {
            // User may not have ACCESS_LOGS permission — silently skip
        }
    })
</script>
<style scoped lang="scss">

#error {
    :deep(.kel-alert__content) {
        cursor: pointer;
        width: 100%;
        max-width: 100%;
        gap: 0;

        & .kel-alert__title {
            & div,
            & span {
                display: flex;
                justify-content: space-between;
                font-size: var(--kel-alert-title-font-size);
                line-height: 24px;
                color: var(--ks-status-error);

            }
        }

        & .kel-alert__description {
            color: var(--ks-text-primary);

            & .logs {
                padding-top: calc(1rem * 1.5);

                > div {
                    width: 100%;

                    & .line {
                        & .header {
                            display: flex;
                            flex-wrap: wrap;
                            margin-bottom: calc(1rem / 2);

                            & span {
                                margin-left: 0;
                            }
                        }
                    }
                }

                .kel-button {
                    color: var(--ks-status-error);
                }

                .link {
                    padding: 1rem 0 calc(1rem / 2) 0;
                    border-top: 1px solid var(--ks-border-default);
                    text-align: right;
                }
            }
        }
    }
}
</style>
