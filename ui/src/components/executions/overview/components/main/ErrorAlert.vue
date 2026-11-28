<template>
    <div class="error-wrapper">
        <KsAlert id="error" type="error" :closable="false" fill>
            <template #title>
                <span class="title">{{ $t('execution_failed') }}</span>
            </template>

            <div v-if="logs.length" class="body">
                <p v-if="!expanded" class="error-preview">
                    {{ $t('last error was') }}: {{ stripBackticks(linkTagsAsText(logs.at(-1)?.message ?? '')) }}
                </p>

                <div v-else class="logs">
                    <div v-for="(log, index) in logs.slice(-MAX_PREVIEW_LOGS)" :key="index">
                        <LogLine
                            :level="log.level"
                            :log="{...log, message: stripBackticks(log.message ?? '')}"
                            :excludeMetas="EXCLUDED_METAS"
                        />
                    </div>
                    <div v-if="logs.length >= MAX_PREVIEW_LOGS" class="link">
                        <router-link :to @click.stop>
                            <KsButton class="errors-button">{{ $t("errorLogs") }}</KsButton>
                        </router-link>
                    </div>
                </div>
            </div>
        </KsAlert>

        <button
            v-if="logs.length"
            class="expand-btn"
            data-test="error-alert-toggle"
            :aria-label="expanded ? $t('collapse') : $t('expand')"
            :aria-expanded="expanded"
            @click="expanded = !expanded"
        >
            <ChevronUp v-if="expanded" />
            <ChevronDown v-else />
        </button>
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue"

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"

    import {Execution, useExecutionsStore} from "../../../../../stores/executions"
    import {Log} from "../../../../../stores/logs"
    import {handledIf} from "../../../../../utils/kestraHttp"
    import LogLine from "../../../../logs/LogLine.vue"
    import {stripBackticks} from "../../../../../utils/logs"
    import {linkTagsAsText} from "../../../../logs/linkify"

    const MAX_PREVIEW_LOGS = 4
    const EXCLUDED_METAS: (keyof Log)[] = ["namespace", "flowId", "executionId"]

    const props = defineProps<{execution: Execution}>()

    const store = useExecutionsStore()

    const expanded = ref(false)
    const logs = ref<Log[]>([])

    const to = {
        name: "executions/update/logs",
        params: {
            tenantId: props.execution.tenantId,
            id: props.execution.id,
            namespace: props.execution.namespace,
            flowId: props.execution.flowId,
        },
        query: {
            "filters[level][GREATER_THAN_OR_EQUAL_TO]": "ERROR",
        },
    }

    const failedAt = (state?: {histories?: {state?: string; date?: string}[]}) =>
        new Date(state?.histories?.findLast(history => history.state === "FAILED")?.date ?? 0).getTime()

    /** The task run that failed first: errors and finally tasks run after it and would otherwise hide its error. */
    const firstFailedTaskRunId = props.execution.taskRunList
        ?.filter(taskRun => taskRun.state?.current === "FAILED")
        .sort((a, b) => failedAt(a.state) - failedAt(b.state))[0]?.id

    const loadErrorLogs = (taskRunId?: string) => store.loadLogs({
        store: false,
        executionId: props.execution.id,
        filters: [
            {field: "level", operation: "GREATER_THAN_OR_EQUAL_TO", value: "ERROR"},
            ...(taskRunId ? [{field: "taskRunId" as const, operation: "EQUALS" as const, value: taskRunId}] : []),
        ],
        size: MAX_PREVIEW_LOGS,
        sort: ["timestamp:desc"],
    })

    onMounted(async () => {
        try {
            // Only the last MAX_PREVIEW_LOGS errors are ever shown, so ask the server
            // for just those (newest first) instead of the full error log list.
            let response = firstFailedTaskRunId ? await loadErrorLogs(firstFailedTaskRunId) : []
            if (!response.length) response = await loadErrorLogs()

            if (response.length) logs.value = [...response].reverse()
        } catch (e: unknown) {
            // The user may lack ACCESS_LOGS; anything else is left to the global toast.
            handledIf(e, [403, 404])
        }
    })
</script>

<style scoped>
    .error-wrapper {
        position: relative;
        min-width: 0;
    }

    .expand-btn {
        position: absolute;
        top: var(--ks-spacing-3);
        right: var(--ks-spacing-3);
        display: inline-flex;
        align-items: center;
        justify-content: center;
        background: none;
        border: none;
        cursor: pointer;
        color: var(--ks-text-error);
        padding: var(--ks-spacing-1);
        border-radius: var(--ks-radius-xs);
        z-index: 1;

        &:hover {
            background: color-mix(in srgb, var(--ks-text-error) 10%, transparent);
        }
    }

    .error-preview {
        margin: 0;
        color: var(--ks-text-error);
        font-size: var(--ks-font-size-sm);
        white-space: pre-wrap;
        word-break: break-word;
        overflow-wrap: anywhere;
    }

    #error {
        min-width: 0;
        overflow: hidden;

        & .title {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding-right: var(--ks-spacing-7);
            font-size: var(--kel-alert-title-font-size);
            line-height: 1.5;
            color: var(--ks-text-error);
        }

        & .body {
            padding-right: var(--ks-spacing-7);
            color: var(--ks-text-primary);
        }

        & .logs {
            --ks-button-link-color: var(--ks-text-error);
            padding-top: var(--ks-spacing-5);

            & > div {
                width: 100%;
            }

            .link {
                padding: var(--ks-spacing-4) 0 var(--ks-spacing-2);
                border-top: 1px solid var(--ks-border-default);
                text-align: right;
            }
        }

        & .errors-button {
            color: var(--ks-text-error);
        }
    }
</style>
