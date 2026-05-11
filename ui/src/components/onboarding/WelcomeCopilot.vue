<template>
    <TopNavBar :title="title">
        <template v-if="$slots.cta" #actions>
            <slot name="cta" />
        </template>
    </TopNavBar>

    <section id="welcome" class="container mt-0">
        <KsRow justify="center">
            <KsCol :xs="24" :sm="24" :md="18" :lg="16" :xl="14">
                <AiCopilot
                    :flow="activeExample.yaml"
                    :conversationId="conversationId"
                    :namespace="namespace"
                    :onboarding="true"
                    :heading="heading"
                    :initialPrompt="activeExample.prompt || undefined"
                    :onboardingExamples="onboardingExamples"
                    :generationType="generationType"
                    :selectedFromTag="selectedIndex !== undefined"
                    :redirectOnUnchangedPrompt="selectedIndex !== undefined"
                    @onboarding-prompt-diverged="clearSelectedTag"
                    @generated-yaml="emit('generatedYaml', $event)"
                    @create-flow-directly="emit('createDirectly', $event)"
                />

                <div class="mt-2 welcome-copilot-tags">
                    <KsTag
                        v-for="(example, i) in visibleExamples"
                        :key="i"
                        round
                        :effect="selectedIndex === i ? 'dark' : 'plain'"
                        :type="selectedIndex === i ? 'primary' : 'info'"
                        @click="selectExample(i)"
                    >
                        {{ example.label }}
                    </KsTag>

                    <KsTag
                        v-if="examples.length > 5"
                        round
                        effect="plain"
                        type="info"
                        @click="allShown = !allShown"
                    >
                        {{ allShown ? $t("welcome_copilot.show_less") : $t("welcome_copilot.show_more") }}
                    </KsTag>
                </div>

                <div v-if="welcomeResources.length > 0" class="welcome-help-section">
                    <p class="welcome-help-title">
                        {{ $t("welcome_copilot.need_help") }}
                    </p>
                    <OnboardingResourceList :items="welcomeResources" />
                </div>
            </KsCol>
        </KsRow>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"

    import TopNavBar from "../layout/TopNavBar.vue"
    import AiCopilot from "../ai/AiCopilot.vue"
    import OnboardingResourceList from "./OnboardingResourceList.vue"
    import {useOnboardingResources} from "./useOnboardingResources"
    import type {OnboardingResourceItem} from "./OnboardingResourceList.vue"

    import type {AiGenerationType} from "../../utils/constants"
    import * as Utils from "../../utils/utils"

    export interface WelcomeCopilotExample {
        label: string;
        prompt: string;
        yaml: string;
    }

    const props = defineProps<{
        title: string;
        heading?: string;
        generationType?: AiGenerationType;
        examples: WelcomeCopilotExample[];
        namespace?: string;
        resources?: OnboardingResourceItem[];
    }>()

    const emit = defineEmits<{
        generatedYaml: [yaml: string];
        createDirectly: [yaml: string];
    }>()

    const conversationId = ref<string>(Utils.uid())
    const selectedIndex = ref<number | undefined>(0)
    const activeIndex = ref<number>(0)

    const activeExample = computed(() => props.examples[activeIndex.value] ?? {label: "", prompt: "", yaml: ""})

    const onboardingExamples = computed(() =>
        props.examples
            .filter((e) => e.prompt.length > 0)
            .map((e) => ({prompt: e.prompt, flow: e.yaml})),
    )

    const allShown = ref(false)
    const visibleExamples = computed(() =>
        allShown.value ? props.examples : props.examples.slice(0, 5),
    )

    const {onboardingResources} = useOnboardingResources()
    const welcomeResources = computed(() =>
        props.resources !== undefined ? props.resources : onboardingResources.value.slice(0, 3),
    )

    function selectExample(index: number) {
        activeIndex.value = index
        selectedIndex.value = index
    }

    function clearSelectedTag() {
        selectedIndex.value = undefined
    }
</script>

<style scoped lang="scss">
    :global(main:has(section#welcome)) {
        max-height: 100%;
        overflow: hidden;
    }

    section#welcome {
        position: relative;
        overflow-x: hidden;
        overflow-y: auto;
        background: url("./assets/background.svg") center top / cover no-repeat;
        height: 100%;
        padding-bottom: 2rem;

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

        .kel-tag {
            cursor: pointer;
            height: 30px;
            margin: calc(1rem / 4);
            border: 1px solid var(--ks-border-primary);
            background-color: var(--ks-button-background-secondary);
            color: var(--ks-content-primary);

            & :deep(.kel-tag__content) {
                padding: 4px 13px;
            }

            &:hover {
                background-color: var(--ks-button-background-secondary-hover);
            }

            &.kel-tag--primary {
                border-color: var(--ks-button-background-primary);
                background-color: var(--ks-button-background-primary);
                color: var(--ks-white);
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
            font-size: var(--ks-font-size-sm);
        }

        :deep(.kel-row) {
            position: relative;
            z-index: 1;
        }
    }
</style>
