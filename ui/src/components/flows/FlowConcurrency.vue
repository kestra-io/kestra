<template>
    <template v-if="flowStore.flow?.concurrency">
        <div v-if="!loading && concurrencyLimit" :class="{'d-none': !runningCountSet}">
            <el-card class="mb-3">
                <div class="row mb-3">
                    <span class="col d-flex align-items-center">
                        <h5 class="m-3">RUNNING</h5> {{ runningCount }}/{{ flowStore.flow?.concurrency?.limit }} {{ $t('active-slots') }}
                    </span>
                    <span class="col d-flex justify-content-end align-items-center">
                        {{ $t('behavior') }}: <Status class="mx-2" :status="flowStore.flow?.concurrency?.behavior" size="small" />
                    </span>
                </div>
                <div class="progressbar mb-3">
                    <el-progress :stroke-width="16" color="#5BB8FF" :percentage="progress" :showText="false" />
                </div>
            </el-card>
            <el-card>
                <Executions
                    :restoreUrl="false"
                    :topbar="false"
                    :namespace="flowStore.flow?.namespace"
                    :flowId="flowStore.flow?.id"
                    filter
                />
            </el-card>
        </div>
        <el-card v-else-if="loading" class="mb-3">
            <div class="text-center">
                <el-icon class="is-loading">
                    <Loading />
                </el-icon>
                <span class="ms-2">{{ $t('loading') }}</span>
            </div>
        </el-card>
        <el-alert v-else-if="error" type="error" :closable="false" showIcon class="mb-3">
            {{ $t('failed to load concurrency limit') }}
        </el-alert>
        <Empty v-else-if="!concurrencyLimit && !loading" type="concurrency_executions" />
    </template>
    <Empty v-else type="concurrency_limit" />
</template>

<script setup lang="ts">
    import {ref, computed, watch, onMounted} from "vue";
    import Executions from "../executions/Executions.vue";
    import Empty from "../layout/empty/Empty.vue";
    import {Status} from "@kestra-io/ui-libs";
    import {useFlowStore} from "../../stores/flow";
    import {useAxios} from "../../utils/axios";
    import {apiUrl} from "override/utils/route";
    import Loading from "vue-material-design-icons/Loading.vue";

    defineOptions({inheritAttrs: false});

    const flowStore = useFlowStore();
    const axios = useAxios();

    const runningCount = ref(0);
    const totalCount = ref(0);
    const runningCountSet = ref(false);
    const loading = ref(false);
    const error = ref<string | undefined>(undefined);
    const concurrencyLimit = ref<{ tenantId: string; namespace: string; flowId: string; running: number } | undefined>(undefined);

    const progress = computed(() => {
        if (!flowStore.flow?.concurrency || concurrencyLimit.value === undefined) return 0;
        return (concurrencyLimit.value.running / flowStore.flow.concurrency.limit) * 100;
    });

    async function loadConcurrencyLimit() {
        if (!flowStore.flow?.namespace || !flowStore.flow?.id) {
            return;
        }

        loading.value = true;
        error.value = undefined;

        try {
            const response = await axios.get(`${apiUrl()}/concurrency-limit/search`);
            const limits = response.data?.results || [];

            const currentFlowLimit = limits.find(
                (limit: any) =>
                    limit.namespace === flowStore.flow?.namespace &&
                    limit.flowId === flowStore.flow?.id
            );

            if (currentFlowLimit) {
                concurrencyLimit.value = currentFlowLimit;
                runningCount.value = currentFlowLimit.running;
                runningCountSet.value = true;
                totalCount.value = currentFlowLimit.running;
            } else {
                concurrencyLimit.value = undefined;
                runningCount.value = 0;
                runningCountSet.value = true;
                totalCount.value = 0;
            }
        } catch (e: any) {
            error.value = e.message;
        } finally {
            loading.value = false;
        }
    }


    watch(
        () => [flowStore.flow?.namespace, flowStore.flow?.id],
        loadConcurrencyLimit,
        {immediate: true}
    );

    onMounted(loadConcurrencyLimit);
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

    :deep(.el-progress) {
        .el-progress-bar, .el-progress-bar__outer, .el-progress-bar__inner {
            border-radius: var(--bs-border-radius);
        }
    }

    :deep(.el-card) {
        background-color: var(--ks-background-panel);
    }

    .text-center {
        text-align: center;
        padding: 20px;
    }
</style>