<template>
    <TopNavBar :title="title">
        <template v-if="$slots.cta" #actions>
            <slot name="cta" />
        </template>
    </TopNavBar>

    <section id="welcome" class="container mt-0">
        <KsRow justify="center">
            <KsCol :xs="24" :sm="24" :md="18" :lg="16" :xl="14" class="welcome-col">
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

                <div class="welcome-copilot-tags">
                    <KsCheckTag
                        v-for="(example, i) in visibleExamples"
                        :key="i"
                        pill
                        :checked="selectedIndex === i"
                        @change="selectExample(i)"
                    >
                        {{ example.label }}
                    </KsCheckTag>

                    <KsCheckTag
                        v-if="examples.length > 5"
                        pill
                        :checked="false"
                        @change="allShown = !allShown"
                    >
                        {{ allShown ? $t("welcome_copilot.show_less") : $t("welcome_copilot.show_more") }}
                        <KsIcon class="show-more-chevron" :class="{'is-open': allShown}">
                            <ChevronDown />
                        </KsIcon>
                    </KsCheckTag>
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

    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

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
        props.resources ?? onboardingResources.value.slice(0, 3),
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
        height: 100%;
        padding-bottom: var(--ks-spacing-6);
        overflow-x: hidden;
        overflow-y: auto;
        background: url("./assets/grid.svg") center top / auto no-repeat;

        .welcome-col {
            max-width: 593px;
        }

        .welcome-copilot-tags {
            position: relative;
            z-index: 1;
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            justify-content: center;
            gap: var(--ks-spacing-3);
            margin: var(--ks-spacing-4) auto var(--ks-spacing-10);
        }

        .show-more-chevron {
            transition: transform 0.2s ease;

            &.is-open {
                transform: rotate(180deg);
            }
        }

        .welcome-help-section {
            position: relative;
            z-index: 1;
            width: calc(100% - var(--ks-spacing-8));
            max-width: 1120px;
            margin: var(--ks-spacing-4) auto 0;
        }

        .welcome-help-title {
            margin: 0 0 var(--ks-spacing-3);
            color: var(--ks-text-dim);
            font-size: var(--ks-font-size-xs);
        }

        :deep(.kel-row) {
            position: relative;
            z-index: 1;
        }

        @media (min-width: 1200px) {
            .welcome-copilot-tags {
                width: 80%;
            }
        }

        @media (max-width: 768px) {
            .welcome-help-section {
                width: calc(100% - var(--ks-spacing-5));
            }
        }
    }
</style>
