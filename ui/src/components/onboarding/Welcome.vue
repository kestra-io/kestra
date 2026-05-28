<template>
    <WelcomeCopilot
        :title="t('ai.flow.title')"
        :generationType="aiGenerationTypes.FLOW"
        :examples="resolvedExamples"
        namespace="tutorial"
        @generated-yaml="createFlowFromGeneratedPrompt"
        @create-directly="createFlowFromSelectedExample"
    >
        <template v-if="canCreateFlow" #cta>
            <router-link :to="{name: 'flows/create'}">
                <KsButton type="primary">
                    {{ $t("welcome_copilot.button_cta") }}
                </KsButton>
            </router-link>
        </template>
    </WelcomeCopilot>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute, useRouter} from "vue-router"

    import WelcomeCopilot from "./WelcomeCopilot.vue"

    import {flowExamples, labels} from "./flows/index"
    import {aiGenerationTypes} from "../../utils/constants"

    import resource from "../../models/resource"
    import action from "../../models/action"

    import {useAuthStore} from "override/stores/auth"
    const authStore = useAuthStore()

    const canCreateFlow = computed(() => {
        return authStore.user?.hasAnyActionOnAnyNamespace(
            resource.FLOW,
            action.CREATE,
        )
    })

    import useRestoreUrl from "../../composables/useRestoreUrl"
    import useRouteContext from "../../composables/useRouteContext"

    import {useI18n} from "vue-i18n"
    const {t, te} = useI18n()
    const route = useRoute()
    const router = useRouter()

    useRestoreUrl()
    useRouteContext(computed(() => ({title: t("ai.flow.title")})))

    const ONBOARDING_FLOW_PRESET_KEY = "kestra.onboarding.flowPreset"

    const resolvedExamples = computed(() =>
        labels.map((label) => ({
            label: t(flowExamples[label].labelKey),
            prompt: te(flowExamples[label].promptKey) ? t(flowExamples[label].promptKey) : "",
            yaml: flowExamples[label].flow,
        })),
    )

    async function createFlowFromSelectedExample(flowSource: string) {
        sessionStorage.setItem(ONBOARDING_FLOW_PRESET_KEY, flowSource)
        await new Promise(resolve => window.setTimeout(resolve, 1000))
        void router.push({name: "flows/create", query: {onboardingPreset: "true"}, params: {tenant: route.params.tenant}})
    }

    async function createFlowFromGeneratedPrompt(flowSource: string) {
        sessionStorage.setItem(ONBOARDING_FLOW_PRESET_KEY, flowSource)
        void router.push({name: "flows/create", query: {onboardingPreset: "true"}, params: {tenant: route.params.tenant}})
    }
</script>
