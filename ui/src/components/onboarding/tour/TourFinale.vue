<template>
    <KsDialog
        v-model="isOpen"
        appendToBody
        width="min(660px, 92vw)"
    >
        <template #header>
            <p class="tour-kicker">
                {{ t("onboarding.tour.finale.kicker") }}
            </p>
            <h1 class="tour-title">{{ t("onboarding.tour.finale.title") }}</h1>
        </template>

        <div class="takeaways">
            <div v-for="takeaway in TAKEAWAYS" :key="takeaway.key" class="takeaway">
                <h4>{{ t(`onboarding.tour.finale.takeaways.${takeaway.key}.title`) }}</h4>
                <p v-html="t(`onboarding.tour.finale.takeaways.${takeaway.key}.body`)" />
                <p class="takeaway-docs">
                    <span>{{ t("onboarding.tour.finale.docs.title") }}</span>
                    <a
                        v-for="name in takeaway.docs"
                        :key="name"
                        :href="docsUrl(name)"
                        target="_blank"
                        rel="noopener"
                    >{{ t(`onboarding.tour.finale.docs.${name}`) }}</a>
                </p>
            </div>
        </div>

        <i18n-t keypath="onboarding.tour.finale.namespace_note" tag="p" class="tour-lead" scope="global">
            <template #namespace>{{ TOUR_NAMESPACE }}</template>
            <template #secrets>
                <a :href="docsUrl('secret')" target="_blank" rel="noopener">
                    {{ t("onboarding.tour.finale.docs.secret") }}
                </a>
            </template>
        </i18n-t>

        <p class="resources-title">
            {{ t("onboarding.tour.finale.keep_going") }}
        </p>
        <OnboardingResourceList :items="resources" />

        <template #footer>
            <KsButton @click="emit('restart')">
                {{ t("onboarding.tour.finale.restart") }}
            </KsButton>
            <KsButton type="primary" @click="startBuilding">
                {{ t("onboarding.tour.finale.start_building") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"

    import OnboardingResourceList from "../OnboardingResourceList.vue"
    import {useOnboardingResources} from "../useOnboardingResources"
    import {TOUR_NAMESPACE} from "./tourFlows"

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()

    const emit = defineEmits<{
        restart: [];
    }>()

    const isOpen = defineModel<boolean>()

    /**
     * Documentation for the concepts the tour went through, kept here rather than in the translations:
     * the URLs are not something to translate, and the rest of the app keeps its doc links in code too.
     */
    const DOCS: Record<string, string> = {
        autocompletion: "https://kestra.io/docs/tutorial/fundamentals#autocompletion",
        execution: "https://kestra.io/docs/workflow-components/execution",
        replay: "https://kestra.io/docs/concepts/replay",
        revision: "https://kestra.io/docs/concepts/revision",
        webhook: "https://kestra.io/docs/workflow-components/triggers/webhook-trigger",
        schedule: "https://kestra.io/docs/workflow-components/triggers/schedule-trigger",
        flowTrigger: "https://kestra.io/docs/workflow-components/triggers/flow-trigger",
        namespace: "https://kestra.io/docs/workflow-components/namespace",
        secret: "https://kestra.io/docs/concepts/secret",
    }

    const DOCS_UTM = "?utm_source=app&utm_medium=referral&utm_campaign=product-tour"

    /** The query goes before the fragment, or the anchor would swallow it. */
    const docsUrl = (name: string) => {
        const [base, fragment] = DOCS[name].split("#")
        return `${base}${DOCS_UTM}${fragment ? `#${fragment}` : ""}`
    }

    const TAKEAWAYS = [
        {key: "copilot", docs: ["autocompletion", "execution"]},
        {key: "restart", docs: ["replay", "revision"]},
        {key: "events", docs: ["webhook", "schedule"]},
        {key: "chain", docs: ["flowTrigger", "namespace"]},
    ] as const

    const {onboardingResources: resources} = useOnboardingResources()

    const startBuilding = async () => {
        isOpen.value = false
        // The same namespace as everything else from the tour, so the story stays consistent.
        await router.push({
            name: "flows/create",
            params: {tenant: route.params.tenant},
            query: {namespace: TOUR_NAMESPACE},
        })
    }
</script>

<style scoped lang="scss">
    .tour-title {
        font-size: var(--ks-font-size-2xl);
    }

    .tour-kicker {
        margin-bottom: var(--ks-spacing-2);
        color: var(--ks-text-link);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-bold);
        letter-spacing: 0.1em;
        text-transform: uppercase;
    }

    .tour-lead {
        margin-bottom: var(--ks-spacing-3);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);

        a {
            color: var(--ks-text-link);
            text-decoration: none;

            &:hover {
                text-decoration: underline;
            }
        }
    }

    .takeaways {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-4);

        @media (max-width: 640px) {
            grid-template-columns: 1fr;
        }
    }

    .takeaway-docs {
        // Pushed to the bottom of the card, so the links of all four line up.
        margin-top: auto !important;
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);
        padding-top: var(--ks-spacing-2);

        span {
            color: var(--ks-text-secondary);
        }

        a {
            color: var(--ks-text-link);
            text-decoration: none;

            &:hover {
                text-decoration: underline;
            }
        }
    }

    .takeaway {
        display: flex;
        flex-direction: column;
        padding: var(--ks-spacing-4);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);

        h4 {
            margin-bottom: var(--ks-spacing-1);
            font-size: var(--ks-font-size-sm);
        }

        p {
            margin: 0;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
        }
    }

    .resources-title {
        margin-bottom: var(--ks-spacing-2);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: var(--ks-font-weight-semibold);
        letter-spacing: 0.06em;
        text-transform: uppercase;
    }
</style>
