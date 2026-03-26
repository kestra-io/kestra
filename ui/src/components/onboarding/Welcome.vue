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

    <section id="welcome" class="container mt-0">
        <el-row justify="center">
            <el-col :xs="24" :sm="24" :md="18" :lg="16" :xl="14">
                <AiCopilot
                    :flow="activeExample.flow"
                    :conversationId="conversationId"
                    namespace="tutorial"
                    :onboarding="true"
                    :initialPrompt="te(activeExample.promptKey) ? t(activeExample.promptKey) : undefined"
                    :onboardingExamples="onboardingExamples"
                    :generationType="aiGenerationTypes.FLOW"
                    :selectedFromTag="selectedLabel !== undefined"
                    :redirectOnUnchangedPrompt="selectedLabel !== undefined"
                    @onboarding-prompt-diverged="clearSelectedTag"
                    @generated-yaml="createFlowFromGeneratedPrompt"
                    @create-flow-directly="createFlowFromSelectedExample"
                />

                <div class="mt-2 welcome-copilot-tags">
                    <el-tag
                        v-for="label in visibleLabels"
                        :key="label"
                        round
                        :effect="selectedLabel === label ? 'dark' : 'plain'"
                        :type="selectedLabel === label ? 'primary' : 'info'"
                        @click="selectLabel(label)"
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

                <div class="welcome-help-section">
                    <p class="welcome-help-title">
                        {{ $t("welcome_copilot.need_help") }}
                    </p>

                    <OnboardingResourceList :items="welcomeResources" />
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useRoute, useRouter} from "vue-router";

    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import AiCopilot from "../ai/AiCopilot.vue";
    import OnboardingResourceList from "./OnboardingResourceList.vue";
    import {useOnboardingResources} from "./useOnboardingResources";

    import {flowExamples, labels} from "./flows/index";
    import {aiGenerationTypes} from "../../utils/constants";

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
    const {t, te} = useI18n();
    const route = useRoute();
    const router = useRouter();

    useRestoreUrl();

    const routeInfo = computed(() => ({title: t("ai.flow.title")}));
    useRouteContext(routeInfo);

    const conversationId = ref<string>(Utils.uid());
    const selectedLabel = ref<(typeof labels)[number] | undefined>(labels[0]);
    const activeLabel = ref<(typeof labels)[number]>(labels[0]);
    const activeExample = computed(() => flowExamples[activeLabel.value]);
    const onboardingExamples = computed(() => labels
        .map((label) => {
            const example = flowExamples[label];
            return {
                prompt: te(example.promptKey) ? t(example.promptKey) : "",
                flow: example.flow,
            };
        })
        .filter((example) => example.prompt.length > 0),
    );

    const allLabelsShown = ref(false);
    const visibleLabels = computed(() => {
        return allLabelsShown.value ? labels : labels.slice(0, 5);
    });

    const ONBOARDING_FLOW_PRESET_KEY = "kestra.onboarding.flowPreset";
    const {onboardingResources} = useOnboardingResources();
    const welcomeResources = computed(() => onboardingResources.value.slice(0, 3));

    function selectLabel(label: (typeof labels)[number]) {
        activeLabel.value = label;
        selectedLabel.value = label;
    }

    function clearSelectedTag() {
        selectedLabel.value = undefined;
    }

    async function createFlowFromSelectedExample(flowSource: string) {
        sessionStorage.setItem(ONBOARDING_FLOW_PRESET_KEY, flowSource);
        await new Promise(resolve => window.setTimeout(resolve, 1000));
        void router.push({name: "flows/create", query: {onboardingPreset: "true"}, params: {tenant: route.params.tenant}});
    }

    async function createFlowFromGeneratedPrompt(flowSource: string) {
        sessionStorage.setItem(ONBOARDING_FLOW_PRESET_KEY, flowSource);
        void router.push({name: "flows/create", query: {onboardingPreset: "true"}, params: {tenant: route.params.tenant}});
    }
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/_variables.scss";

    section#welcome {
        position: relative;
        overflow: hidden;
        background: url("./assets/background.svg") center top / cover no-repeat;
        min-height: calc(100vh - 60px);

        .welcome-copilot-tags {
            display: flex;
            justify-content: center;
            align-items: center;
            flex-wrap: wrap;
            margin: 0 auto;
            position: relative;
            z-index: 1;
        }

        @media (min-width: 1200px) {
            .welcome-copilot-tags {
                width: 80%;
            }
        }

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

        .welcome-help-section {
            width: calc(100% - 48px);
            max-width: 1120px;
            margin: 1rem auto 0;
            position: relative;
            z-index: 1;
        }

        @media (max-width: 768px) {
            .welcome-help-section {
                width: calc(100% - 24px);
            }
        }

        .welcome-help-title {
            margin: 0 0 0.875rem;
            color: var(--ks-content-secondary);
            font-size: $font-size-sm;
        }

        :deep(.el-row) {
            position: relative;
            z-index: 1;
        }

    }
</style>
