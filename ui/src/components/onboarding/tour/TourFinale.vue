<template>
    <KsDialog
        v-model="isOpen"
        appendToBody
        width="min(660px, 92vw)"
        scrollable
    >
        <template #header>
            <p class="tour-kicker">
                {{ $t("onboarding.tour.finale.kicker") }}
            </p>
            <h1 class="tour-title">{{ $t("onboarding.tour.finale.title") }}</h1>
        </template>

        <div class="takeaways">
            <div v-for="takeaway in TAKEAWAYS" :key="takeaway.key" class="takeaway">
                <h4>{{ $t(`onboarding.tour.finale.takeaways.${takeaway.key}.title`) }}</h4>
                <p v-html="$t(`onboarding.tour.finale.takeaways.${takeaway.key}.body`)" />
                <p class="takeaway-docs">
                    <span>{{ $t("onboarding.tour.finale.docs.title") }}</span>
                    <a
                        v-for="name in takeaway.docs"
                        :key="name"
                        :href="docsUrl(name)"
                        target="_blank"
                        rel="noopener"
                    >{{ $t(`onboarding.tour.finale.docs.${name}`) }}</a>
                </p>
            </div>
        </div>

        <p class="tour-lead" v-html="namespaceNote" />

        <p class="resources-title">
            {{ $t("onboarding.tour.finale.keep_going") }}
        </p>
        <OnboardingResourceList :items="resources" />

        <template #footer>
            <KsButton @click="emit('restart')">
                {{ $t("onboarding.tour.finale.restart") }}
            </KsButton>
            <KsButton type="primary" @click="startBuilding">
                {{ $t("onboarding.tour.finale.start_building") }}
            </KsButton>
        </template>
    </KsDialog>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import {useOnboardingResources} from "../useOnboardingResources"
    import OnboardingResourceList from "../OnboardingResourceList.vue"
    import {TOUR_NAMESPACE} from "./tourFlows"

    const emit = defineEmits<{
        restart: [];
    }>()

    const isOpen = defineModel<boolean>()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const {onboardingResources: resources} = useOnboardingResources()

    const DOCS = {
        copilot: "https://kestra.io/docs/ai-tools/ai-copilot",
        agentSkills: "https://kestra.io/docs/ai-tools/agent-skills",
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

    const docsUrl = (name: keyof typeof DOCS) => {
        const [base, fragment] = DOCS[name].split("#")
        return `${base}${DOCS_UTM}${fragment ? `#${fragment}` : ""}`
    }

    const namespaceNote = computed(() =>
        t("onboarding.tour.finale.namespace_note", {
            namespace: TOUR_NAMESPACE,
            secrets: `<a href="${docsUrl("secret")}" target="_blank" rel="noopener">${t("onboarding.tour.finale.docs.secret")}</a>`,
        }))

    const TAKEAWAYS = [
        {key: "copilot", docs: ["copilot", "agentSkills", "autocompletion"]},
        {key: "restart", docs: ["replay", "revision"]},
        {key: "events", docs: ["webhook", "schedule"]},
        {key: "chain", docs: ["flowTrigger", "namespace"]},
    ] as const

    // `blank` skips the creation funnel: the tour has just guided the user for twenty
    // minutes and ends on "now build one yourself", so a chooser here is a step backwards.
    const startBuilding = async () => {
        isOpen.value = false
        await router.push({
            name: "flows/create",
            params: {tenant: route.params.tenant},
            query: {namespace: TOUR_NAMESPACE, blank: "true"},
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

        :deep(a) {
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

    .takeaway .takeaway-docs {
        margin-top: auto;
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
