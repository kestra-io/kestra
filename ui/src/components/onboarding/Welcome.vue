<template>
    <TopNavBar :title="routeInfo.title">
        <template #additional-right>
            <router-link v-if="canCreateFlow" :to="{name: 'flows/create'}">
                <el-button type="primary">
                    {{ $t("welcome_copilot.button_cta") }}
                </el-button>
            </router-link>
        </template>
    </TopNavBar>

    <section id="welcome" class="container">
        <el-row justify="center">
            <el-col :xs="24" :sm="24" :md="18" :lg="16" :xl="14">
                <AiCopilot
                    :flow="selectedExample.flow"
                    :conversationId="conversationId"
                    :onboarding="true"
                    :initialPrompt="t(selectedExample.promptKey)"
                />

                <div class="mt-3">
                    <el-tag
                        v-for="label in visibleLabels"
                        :key="label"
                        round
                        :effect="selectedLabel === label ? 'dark' : 'plain'"
                        :type="selectedLabel === label ? 'primary' : 'info'"
                        @click="selectedLabel = label"
                    >
                        {{ t(flowExamples[label].labelKey) }}
                    </el-tag>

                    <el-tag
                        v-if="labels.length > 5"
                        round
                        effect="plain"
                        type="info"
                        @click="allLabelsShown = !allLabelsShown"
                    >
                        {{
                            allLabelsShown
                                ? $t("welcome_copilot.show_less")
                                : $t("welcome_copilot.show_more")
                        }}
                    </el-tag>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";

    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import AiCopilot from "../ai/AiCopilot.vue";

    import {flowExamples, initialFlow, labels} from "./flows/index";

    import permission from "../../models/permission";
    import action from "../../models/action";

    import {useAuthStore} from "override/stores/auth";
    const authStore = useAuthStore();

    const canCreateFlow = computed(() => {
        return authStore.user?.hasAnyActionOnAnyNamespace(
            permission.FLOW,
            action.CREATE,
        );
    });

    import useRestoreUrl from "../../composables/useRestoreUrl";
    import useRouteContext from "../../composables/useRouteContext";

    import Utils from "../../utils/utils";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n();

    useRestoreUrl();

    const routeInfo = computed(() => ({title: t("ai.flow.title")}));
    useRouteContext(routeInfo);

    const conversationId = ref<string>(Utils.uid());
    const selectedLabel = ref<(typeof labels)[number] | undefined>(undefined);
    const selectedExample = computed(
        () =>
            (selectedLabel.value
                ? flowExamples[selectedLabel.value]
                : undefined) ?? {
                flow: initialFlow,
                labelKey: "",
                promptKey: "",
            },
    );

    const allLabelsShown = ref(false);
    const visibleLabels = computed(() => {
        return allLabelsShown.value ? labels : labels.slice(0, 5);
    });
</script>

<style scoped lang="scss">
section#welcome {
    .el-tag {
        cursor: pointer;
        height: 30px;
        margin: calc(1rem / 4);
        border: 1px solid var(--ks-border-primary);
        background-color: var(--ks-button-background-secondary);
        color: var(--ks-content-primary);

        & :deep(.el-tag__content) {
            padding: 4px 13px;
        }

        &:hover {
            background-color: var(--ks-button-background-secondary-hover);
        }

        &.el-tag--primary {
            border-color: var(--el-color-primary);
            background-color: var(--el-color-primary);
            color: white;
        }
    }
}
</style>
