<template>
    <KsCard v-if="loading && flowStore.flow?.concurrency" class="mb-3">
        <div class="text-center">
            <KsIcon class="is-loading">
                <Loading />
            </KsIcon>
            <span class="ms-2">{{ $t('loading') }}</span>
        </div>
    </KsCard>
    <KsAlert v-else-if="error && flowStore.flow?.concurrency" type="error" :closable="false" class="mb-3">
        {{ $t('flowConcurrency.loadError') }}
    </KsAlert>
    <div v-else-if="concurrencyLimit && flowStore.flow?.concurrency" data-test="concurrency-limit">
        <KsCard class="concurrency-summary mb-3">
            <div class="row mb-3">
                <span class="col d-flex align-items-center">
                    <h5 class="m-3">RUNNING</h5> {{ concurrencyLimit.running }}/{{ flowStore.flow?.concurrency?.limit }} {{ $t('active-slots') }}
                </span>
                <span class="col d-flex justify-content-end align-items-center">
                    {{ $t('behavior') }}: <KsExecutionStatus class="mx-2" :status="flowStore.flow?.concurrency?.behavior" size="small" />
                </span>
            </div>
            <div class="progressbar mb-3">
                <KsProgress :stroke-width="16" color="#5BB8FF" :percentage="progress" :showText="false" />
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
        class="mb-3"
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
                {ignoreNotFound: true, showMessageOnError: false},
            )

            concurrencyLimit.value = response.data
        } catch (err: any) {
            if (err?.status === 404 || err?.response?.status === 404) {
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

<style scoped lang="scss">
    .img-size {
        max-width: 200px;
    }
    .bg-purple {
        height: 100%;
        width: 100%;
    }
    h5 {
        font-weight: bold;
        margin-left: 0 !important;
    }

    :deep(.kel-progress) {
        .kel-progress-bar, .kel-progress-bar__outer, .kel-progress-bar__inner {
            border-radius: var(--kel-border-radius-base);
        }
    }

    :deep(.kel-card) {
        background-color: var(--ks-bg-surface);
    }

    .concurrency-summary {
        margin-inline: var(--ks-spacing-6);
    }

    .text-center {
        text-align: center;
        padding: var(--ks-font-size-lg);
    }
</style>