<template>
    <EmptyTemplate plain>
        <div class="content">
            <img
                :src="executeimg"
                alt="Kestra"
            >
            <h2 class="title">
                {{ $t("no-executions-view.title") }}
            </h2>
            <p class="desc">
                {{ $t(subTitleKey) }}
            </p>
        </div>
        <div class="bottom">
            <p class="label">
                {{ $t("no-executions-view.need_help") }}
            </p>
            <OverviewBottom :isNamespace />
        </div>
    </EmptyTemplate>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import OverviewBottom from "../onboarding/execution/OverviewBottom.vue"
    import EmptyTemplate from "../layout/EmptyTemplate.vue"
    import executeimg from "../../assets/onboarding/execute.svg"
    import {useFlowStore} from "../../stores/flow"

    const props = defineProps<{isNamespace?: boolean}>()

    const flowStore = useFlowStore()

    const subTitleKey = computed(() => {
        if (props.isNamespace) return "no-executions-view.namespace_sub_title"
        return flowStore.isDraftOnly ? "no-executions-view.draft_sub_title" : "no-executions-view.sub_title"
    })
</script>

<style scoped>
.empty-template {
    min-height: calc(100vh - 174px);
    min-height: calc(100dvh - 174px);
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: var(--ks-spacing-10);

    .content {
        width: 100%;
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-2);
        max-width: 590px;

        .title {
            margin: 0;
            line-height: var(--ks-line-height-base);
            text-align: center;
            font-size: var(--ks-font-size-xl);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }

        .desc {
            margin: 0;
            font-weight: var(--ks-font-weight-regular);
            font-size: var(--ks-font-size-md);
            text-align: center;
            color: var(--ks-text-secondary);
        }
    }

    .bottom {
        width: 100%;
        max-width: 590px;

        .label {
            margin-bottom: var(--ks-spacing-3);
            text-align: left;
            font-size: var(--ks-font-size-sm);
            color: var(--ks-text-secondary);
        }
    }
}
</style>
