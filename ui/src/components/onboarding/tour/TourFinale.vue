<template>
    <div class="tour-scrim">
        <div class="tour-card">
            <p class="tour-kicker">
                {{ t("onboarding.tour.finale.kicker") }}
            </p>
            <h1>{{ t("onboarding.tour.finale.title") }}</h1>

            <div class="takeaways">
                <div v-for="takeaway in TAKEAWAYS" :key="takeaway.key" class="takeaway">
                    <h4>{{ t(`onboarding.tour.finale.takeaways.${takeaway.key}.title`) }}</h4>
                    <!-- As in the guide card: the names of the controls are in <strong>. -->
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

            <!-- Where the tour's flows live, and what it cut corners on, so nobody copies the mock
                 endpoint into production. The link is built here: URLs are not translated. -->
            <p class="tour-lead" v-html="namespaceNote" />

            <p class="resources-title">
                {{ t("onboarding.tour.finale.keep_going") }}
            </p>
            <OnboardingResourceList :items="resources" />

            <div class="tour-card-actions">
                <KsButton @click="emit('restart')">
                    {{ t("onboarding.tour.finale.restart") }}
                </KsButton>
                <KsButton type="primary" @click="startBuilding">
                    {{ t("onboarding.tour.finale.start_building") }}
                </KsButton>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
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
        close: [];
    }>()

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

    const namespaceNote = computed(() => t("onboarding.tour.finale.namespace_note", {
        namespace: TOUR_NAMESPACE,
        secrets: `<a href="${docsUrl("secret")}" target="_blank" rel="noopener">`
            + t("onboarding.tour.finale.docs.secret")
            + "</a>",
    }))

    const startBuilding = async () => {
        emit("close")
        // The same namespace as everything else from the tour, so the story stays consistent.
        await router.push({
            name: "flows/create",
            params: {tenant: route.params.tenant},
            query: {namespace: TOUR_NAMESPACE},
        })
    }
</script>

<style scoped lang="scss">
    .tour-scrim {
        position: fixed;
        inset: 0;
        z-index: 5200;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 1.5rem;
        background: color-mix(in srgb, var(--ks-bg-base) 80%, transparent);
        backdrop-filter: blur(6px);
        overflow-y: auto;
    }

    .tour-card {
        width: 100%;
        max-width: 660px;
        // Keeps the buttons reachable on short screens.
        max-height: calc(100vh - 3rem);
        overflow-y: auto;
        padding: 2rem;
        border: 1px solid var(--ks-border-default);
        border-radius: 14px;
        background: var(--ks-bg-elevated, var(--ks-bg-surface));
        box-shadow: 0 24px 80px rgba(0, 0, 0, 0.55);

        h1 {
            margin-bottom: 0.75rem;
            font-size: var(--ks-font-size-2xl, 1.5rem);
        }
    }

    .tour-kicker {
        margin-bottom: 0.5rem;
        color: var(--ks-content-link, var(--ks-text-link));
        font-size: var(--ks-font-size-xs);
        font-weight: 700;
        letter-spacing: 0.1em;
        text-transform: uppercase;
    }

    .tour-lead {
        margin-bottom: 0.75rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);

        :deep(a) {
            color: var(--ks-content-link, var(--ks-text-link));
            text-decoration: none;

            &:hover {
                text-decoration: underline;
            }
        }
    }

    .tour-card-actions {
        display: flex;
        justify-content: flex-end;
        gap: 0.5rem;
        margin-top: 1.5rem;
    }

    .takeaways {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 0.75rem;
        margin: 1rem 0;

        @media (max-width: 640px) {
            grid-template-columns: 1fr;
        }
    }

    .takeaway-docs {
        // Pushed to the bottom of the card, so the links of all four line up.
        margin-top: auto !important;
        display: flex;
        flex-wrap: wrap;
        gap: 0.5rem;
        padding-top: 0.5rem;

        span {
            color: var(--ks-text-secondary);
        }

        a {
            color: var(--ks-content-link, var(--ks-text-link));
            text-decoration: none;

            &:hover {
                text-decoration: underline;
            }
        }
    }

    .takeaway {
        display: flex;
        flex-direction: column;
        padding: 1rem;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);

        h4 {
            margin-bottom: 0.25rem;
            font-size: var(--ks-font-size-sm);
        }

        p {
            margin: 0;
            color: var(--ks-text-secondary);
            font-size: var(--ks-font-size-xs);
        }
    }

    .resources-title {
        margin-bottom: 0.5rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        letter-spacing: 0.06em;
        text-transform: uppercase;
    }
</style>
