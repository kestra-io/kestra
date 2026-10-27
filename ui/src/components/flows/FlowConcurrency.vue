<template>
    <KsCard v-if="loading && flowStore.flow?.concurrency" class="concurrency-section">
        <div class="loading-state">
            <KsIcon class="is-loading">
                <Loading />
            </KsIcon>
            <span class="loading-label">{{ $t('loading') }}</span>
        </div>
    </KsCard>
    <KsAlert v-else-if="error && flowStore.flow?.concurrency" type="error" :closable="false" class="concurrency-section">
        {{ $t('flowConcurrency.loadError') }}
    </KsAlert>
    <div v-else-if="concurrencyLimit && flowStore.flow?.concurrency" data-test="concurrency-limit">
        <KsCard class="concurrency-summary concurrency-section">
            <div class="concurrency-header">
                <span class="running-slots">
                    <h5 class="running-label">RUNNING</h5> {{ concurrencyLimit.running }}/{{ flowStore.flow?.concurrency?.limit }} {{ $t('active-slots') }}
                </span>
                <span class="concurrency-behavior">
                    {{ $t('behavior') }}: <KsExecutionStatus class="concurrency-status" :status="flowStore.flow?.concurrency?.behavior" size="small" />
                </span>
            </div>
            <div class="concurrency-section">
                <KsProgress :stroke-width="16" color="var(--ks-status-info)" radius="var(--ks-radius-base)" :percentage="progress" :showText="false" />
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
    <!-- A limit record still counting slots for a flow that no longer declares a concurrency
         block: it cannot be rendered as a ratio, but we should display it anyway. -->
    <KsAlert
        v-else-if="staleLimit"
        type="warning"
        :closable="false"
        :title="$t('flowConcurrency.staleLimit.title')"
        class="concurrency-section"
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
    const concurrencyLimit = ref<{ tenantId: string; namespace: string; flowId: string; running: number } | undefined>(undefined)

    const progress = computed(() => {
        if (!flowStore.flow?.concurrency || concurrencyLimit.value === undefined) return 0
        return (concurrencyLimit.value.running / flowStore.flow.concurrency.limit) * 100
    })

    // A leftover record holding slots for a flow that no longer declares a concurrency block.
    // A record sitting at zero is harmless noise, so only a non-zero count is surfaced.
    const staleLimit = computed(() => {
        if (flowStore.flow?.concurrency || !concurrencyLimit.value?.running) return undefined
        return concurrencyLimit.value
    })

    async function loadConcurrencyLimit() {
        if (!flowStore.flow?.namespace || !flowStore.flow?.id) {
            return
        }

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
    .concurrency-section, .concurrency-header {
        margin-bottom: var(--ks-spacing-4);
    }

    .loading-state {
        text-align: center;
        padding: var(--ks-spacing-4);
    }

    .loading-label {
        margin-inline-start: var(--ks-spacing-2);
    }

    .concurrency-summary {
        margin-inline: var(--ks-spacing-6);
    }

    .running-slots, .concurrency-behavior {
        display: flex;
        align-items: center;
    }

    .running-label {
        font-weight: var(--ks-font-weight-bold);
        margin: var(--ks-spacing-4);
        margin-inline-start: 0;
    }

    .concurrency-behavior {
        justify-content: flex-end;
    }

    .concurrency-status {
        margin-inline: var(--ks-spacing-2);
    }
</style>
