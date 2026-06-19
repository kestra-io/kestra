<template>
    <div class="error-wrapper">
        <KsAlert id="error" type="error" :closable="false" @click="expanded = !expanded">
            <template #title>
                <span v-if="logs.at(-1)?.message">{{ $t('execution_failed') }}</span>
            </template>

            <div v-if="logs.length">
                <p v-if="!expanded" class="error-preview">
                    {{ $t('last error was') }}: {{ stripBackticks(logs.at(-1)?.message ?? '') }}
                </p>

                <div v-else class="logs">
                    <div v-for="(log, index) in logs.slice(0, MAX_PREVIEW_LOGS)" :key="index">
                        <LogLine
                            :level="log.level"
                            :log="{...log, message: stripBackticks(log.message ?? '')}"
                            :excludeMetas="EXCLUDED_METAS"
                        />
                    </div>
                    <div v-if="logs.length >= MAX_PREVIEW_LOGS" class="link">
                        <router-link :to @click.stop>
                            <KsButton>{{ $t("errorLogs") }}</KsButton>
                        </router-link>
                    </div>
                </div>
            </div>
        </KsAlert>

        <button class="expand-btn" @click="expanded = !expanded">
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
    import LogLine from "../../../../logs/LogLine.vue"

    const MAX_PREVIEW_LOGS = 4
    const EXCLUDED_METAS: (keyof Log)[] = ["namespace", "flowId", "executionId"]
    const BACKTICKS_REGEX = /`([^`]*)`/g

    const props = defineProps<{execution: Execution}>()

    const store = useExecutionsStore()

    const expanded = ref(false)
    const logs = ref<Log[]>([])

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

    function stripBackticks(message: string): string {
        return message.replace(BACKTICKS_REGEX, "$1")
    }

    onMounted(async () => {
        try {
            const response = await store.loadLogs({
                store: false,
                executionId: props.execution.id,
                params: {minLevel: "ERROR"},
                showMessageOnError: false,
            })

            if (response.length) logs.value = response
        } catch {
            // User may not have ACCESS_LOGS permission — silently skip
        }
    })
</script>

<style scoped lang="scss">
    .error-wrapper {
        position: relative;
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
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    #error {
        overflow: hidden;
        cursor: pointer;

        :deep(.kel-alert__content) {
            min-width: 0;
            width: 100%;
            gap: 0;

            .kel-alert__title > div,
            .kel-alert__title > span {
                display: flex;
                justify-content: space-between;
                align-items: center;
                font-size: var(--kel-alert-title-font-size);
                line-height: 1.5;
                color: var(--ks-text-error);
            }

            .kel-alert__description {
                min-width: 0;
                overflow: hidden;
                color: var(--ks-text-primary);

                .logs {
                    padding-top: var(--ks-spacing-5);

                    > div {
                        width: 100%;
                    }

                    .line .header {
                        display: flex;
                        flex-wrap: wrap;
                        margin-bottom: var(--ks-spacing-2);

                        span {
                            margin-left: 0;
                        }
                    }

                    .kel-button {
                        color: var(--ks-text-error);
                    }

                    .link {
                        padding: var(--ks-spacing-4) 0 var(--ks-spacing-2);
                        border-top: 1px solid var(--ks-border-default);
                        text-align: right;
                    }
                }
            }
        }
    }
</style>
