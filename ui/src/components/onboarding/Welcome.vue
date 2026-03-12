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

                <div class="mt-2 welcome-copilot-tags">
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

                <div class="welcome-help-section">
                    <p class="welcome-help-title">
                        {{ $t("welcome_copilot.need_help") }}
                    </p>

                    <div class="welcome-help-list">
                        <component
                            :is="item.href ? 'a' : 'router-link'"
                            v-for="item in helpItems"
                            :key="item.titleKey"
                            class="welcome-help-item"
                            :href="item.href"
                            :to="item.to"
                            :target="item.href ? '_blank' : undefined"
                            :rel="item.href ? 'noreferrer' : undefined"
                        >
                            <div class="welcome-help-item__icon" :class="item.iconClass">
                                <component :is="item.icon" />
                            </div>

                            <div class="welcome-help-item__content">
                                <h3>{{ $t(item.titleKey) }}</h3>
                                <p>{{ $t(item.descriptionKey) }}</p>
                            </div>

                            <ChevronRight class="welcome-help-item__arrow" />
                        </component>
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import BookOpenVariant from "vue-material-design-icons/BookOpenVariant.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import AiCopilot from "../ai/AiCopilot.vue";
    import SlackLogo from "./components/SlackLogo.vue";

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

    const helpItems = [
        {
            titleKey: "welcome_copilot.help.tutorial.title",
            descriptionKey: "welcome_copilot.help.tutorial.description",
            icon: PlayBoxMultiple,
            iconClass: "is-tutorial",
            to: {name: "flows/create", query: {onboarding: "guided", reset: "true"}},
        },
        {
            titleKey: "welcome_copilot.help.blueprints.title",
            descriptionKey: "welcome_copilot.help.blueprints.description",
            icon: BookOpenVariant,
            iconClass: "is-blueprints",
            to: {name: "blueprints", params: {kind: "flow", tab: "all"}},
        },
        {
            titleKey: "welcome_copilot.help.slack.title",
            descriptionKey: "welcome_copilot.help.slack.description",
            icon: SlackLogo,
            iconClass: "is-slack",
            href: "https://kestra.io/slack",
        },
    ] as const;
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/_variables.scss";

section#welcome {
    .welcome-copilot-tags {
        display: flex;
        justify-content: center;
        align-items: center;
        flex-wrap: wrap;
        margin: 0 auto;
    }

    @media (min-width: 1200px) {
        .welcome-copilot-tags {
            width: 80%;
        }
    }

     @media (min-width: 1920px) {
        .welcome-copilot-tags {
            width: 60%;
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

    .welcome-help-list {
        overflow: hidden;
        border: 1px solid var(--ks-border-primary);
        border-radius: 14px;
        background: var(--ks-background-card);
    }

    .welcome-help-item {
        display: flex;
        align-items: center;
        gap: 1rem;
        padding: 1rem 1.25rem;
        color: inherit;
        cursor: pointer;
        text-decoration: none;
        transition: background-color 0.15s ease;

        &:not(:last-child) {
            border-bottom: 1px solid var(--ks-border-primary);
        }

        &:hover {
            background: var(--ks-button-background-secondary);
            text-decoration: none;
        }
    }

    .welcome-help-item__icon {
        display: grid;
        place-items: center;
        width: 28px;
        height: 28px;
        flex-shrink: 0;

        &:deep(svg) {
            width: 22px;
            height: 22px;
        }

        &.is-tutorial {
            color: #4dabf7;
        }

        &.is-blueprints {
            color: #8b5cf6;
        }

        &.is-slack {
            color: #22c55e;
        }
    }

    .welcome-help-item__content {
        flex: 1;
        min-width: 0;

        h3 {
            margin: 0 0 0.25rem;
            color: var(--ks-content-primary);
            font-size: $font-size-sm;
            font-weight: 600;
        }

        p {
            margin: 0;
            color: var(--ks-content-secondary);
            font-size: $font-size-sm;
            line-height: 1.4;
        }
    }

    .welcome-help-item__arrow {
        color: var(--ks-content-tertiary);
        flex-shrink: 0;
    }
}
</style>
