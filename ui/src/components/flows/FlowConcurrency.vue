<template>
    <KsCard v-if="loading && flowStore.flow?.concurrency" class="flow-concurrency-message">
        <div class="loading-state">
            <KsIcon class="is-loading">
                <Loading />
            </KsIcon>
            <span>{{ $t('loading') }}</span>
        </div>
    </KsCard>

    <KsAlert
        v-else-if="error && flowStore.flow?.concurrency"
        type="error"
        :closable="false"
        class="flow-concurrency-message"
    >
        {{ $t('flowConcurrency.loadError') }}
    </KsAlert>

    <div v-else-if="concurrencyLimit && flowStore.flow?.concurrency" data-test="concurrency-limit">
        <KsCard class="concurrency-summary">
            <div class="concurrency-summary-row">
                <span class="concurrency-summary-side">
                    <h5 class="concurrency-summary-title">RUNNING</h5>
                    {{ concurrencyLimit.running }}/{{ flowStore.flow?.concurrency?.limit }}
                    {{ $t('active-slots') }}
                </span>

                <span class="concurrency-summary-side concurrency-summary-side--end">
                    {{ $t('behavior') }}:
                    <KsExecutionStatus
                        :status="flowStore.flow?.concurrency?.behavior"
                        size="small"
                    />
                </span>
            </div>

            <div class="progressbar">
                <KsProgress
                    :stroke-width="16"
                    color="var(--ks-status-info)"
                    :percentage="progress"
                    :showText="false"
                />
            </div>
        </KsCard>

        <Executions
            :restoreUrl="false"
            :topbar="false"
            :namespace="flowStore.flow?.namespace"
            :flowId="flowStore.flow?.id"
            filter
        />
    </div>

    <KsAlert
        v-else-if="staleLimit"
        type="warning"
        :closable="false"
        :title="$t('flowConcurrency.staleLimit.title')"
        class="flow-concurrency-message"
        data-test="concurrency-stale-limit"
    >
        {{ $t('flowConcurrency.staleLimit.message', {count: staleLimit.running}) }}
    </KsAlert>

    <Empty v-else-if="flowStore.flow?.concurrency" type="concurrency_executions" />
    <Empty v-else type="concurrency_limit" />
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import Executions from "../executions/Executions.vue"
    import Empty from "../layout/empty/Empty.vue"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import {useFlowStore} from "../../stores/flow"
    import {useClient} from "@kestra-io/kestra-sdk"
    import {apiUrl} from "override/utils/route"
    import {handled, type KestraHttpError} from "../../utils/kestraHttp"
    import Loading from "vue-material-design-icons/Loading.vue"

    defineOptions({inheritAttrs: false})

    const flowStore = useFlowStore()
    const axios = useClient()

    const loading = ref(false)
    const error = ref(false)
    const concurrencyLimit = ref<{
        tenantId: string
        namespace: string
        flowId: string
        running: number
    } | undefined>(undefined)

    const progress = computed(() => {
        if (!flowStore.flow?.concurrency || concurrencyLimit.value === undefined) return 0
        return (concurrencyLimit.value.running / flowStore.flow.concurrency.limit) * 100
    })

    const staleLimit = computed(() => {
        if (flowStore.flow?.concurrency || !concurrencyLimit.value?.running) return undefined
        return concurrencyLimit.value
    })

    async function loadConcurrencyLimit() {
        if (!flowStore.flow?.namespace || !flowStore.flow?.id) return

        loading.value = true
        error.value = false

        try {
            const response = await axios.get(
                `${apiUrl()}/concurrency-limit/${flowStore.flow.namespace}/${flowStore.flow.id}`,
            )

            concurrencyLimit.value = response.data
        } catch (err) {
            const httpError = err as KestraHttpError | undefined

            if (httpError?.status === 404 || httpError?.response?.status === 404) {
                handled(err)
                concurrencyLimit.value = undefined
            } else {
                error.value = true
            }
        } finally {
            loading.value = false
        }
    }

    watch(
        () => [flowStore.flow?.namespace, flowStore.flow?.id],
        loadConcurrencyLimit,
        {immediate: true},
    )
</script>

<style scoped>
    .img-size {
        max-width: 200px;
    }

    .bg-purple {
        height: 100%;
        width: 100%;
    }

    :deep(.kel-progress) {
        .kel-progress-bar,
        .kel-progress-bar__outer,
        .kel-progress-bar__inner {
            border-radius: var(--kel-border-radius-base);
        }
    }

    :deep(.kel-card) {
        background-color: var(--ks-bg-surface);
    }

    .concurrency-summary {
        margin-inline: var(--ks-spacing-6);
        margin-bottom: var(--ks-spacing-4);
    }

    .flow-concurrency-message {
        margin-bottom: var(--ks-spacing-4);
    }

    .loading-state {
        display: flex;
        align-items: center;
        justify-content: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-font-size-lg);
    }

    .concurrency-summary-row {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-4);
        margin-bottom: var(--ks-spacing-4);
    }

    .concurrency-summary-side {
        display: flex;
        align-items: center;
        flex: 1;
    }

    .concurrency-summary-side--end {
        justify-content: flex-end;
        gap: var(--ks-spacing-2);
    }

    .concurrency-summary-title {
        margin: var(--ks-spacing-4) 0;
        font-weight: bold;
    }
</style>
