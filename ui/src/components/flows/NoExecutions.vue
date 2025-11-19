<template>
    <EmptyTemplate>
        <div class="content">
            <img
                :src="noexecutionimg"
                alt="Kestra"
                class="logo img-fluid"
            >
            <h2 class="title">
                {{ $t("no-executions-view.title") }}
            </h2>
            <p class="desc">
                {{ isNamespace ? $t("no-executions-view.namespace_sub_title") : $t("no-executions-view.sub_title") }}
            </p>
            <div v-if="flow && !flow.deleted" class="trigger">
                <TriggerFlow
                    type="primary"
                    :disabled="flow.disabled"
                    :flowId="flow.id"
                    :namespace="flow.namespace"
                    :flowSource="flow.source"
                />
            </div>
        </div>
        <el-divider>
            {{ isNamespace ? $t("no-executions-view.namespace_guidance_desc") : $t("welcome_page.guide") }}
        </el-divider>
        <OverviewBottom class="bottom" :isNamespace />
    </EmptyTemplate>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import OverviewBottom from "../onboarding/execution/OverviewBottom.vue";
    import EmptyTemplate from "../layout/EmptyTemplate.vue";
    import noexecutionimg from "../../assets/onboarding/noexecution.svg"
    import {useFlowStore} from "../../stores/flow"
    //@ts-expect-error no declaration file
    import TriggerFlow from "../flows/TriggerFlow.vue"

    withDefaults(defineProps<{topbar?: boolean; isNamespace?: boolean}>(), {
        topbar: true,
        isNamespace: false,
    })

    const flowStore = useFlowStore();
    const flow = computed(() => flowStore.flow)
</script>

<style scoped lang="scss">
.content {
    width: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    max-width: 434px;
    margin: 4rem auto;

    .title {
        line-height: var(--el-font-line-height-primary);
        text-align: center;
        font-size: var(--el-font-size-extra-large);
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .desc {
        line-height: var(--el-font-line-height-primary);
        font-weight: 300;
        font-size: 16px;
        line-height: 28px;
        text-align: center;
        color: var(--ks-content-primary);
    }

    .trigger {
        :deep(.el-button) {
            width: 226px;
            height: 45px;
        }
    }
}

:deep(.el-divider) {
    max-width: 746px;
    margin: 0 auto;
}

:deep(.el-divider__text) {
    color: var(--ks-content-secondary);
    white-space: nowrap;
    font-size: var(--el-font-size-extra-small);
}

.bottom {
    max-width: 746px;
    margin: 2rem auto;
}
</style>
