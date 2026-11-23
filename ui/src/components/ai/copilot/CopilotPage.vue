<template>
    <!--
        Full-page host for the v2 AI Copilot — reuses the same CopilotChat as the right-side dock in the
        "page" layout (the #7909 onboarding cutover, making the copilot a discoverable full-width home).
        The legacy one-shot generator (`../AiCopilot.vue` + onboarding WelcomeCopilot) is retired by this
        change.
    -->
    <!-- Hidden for a user without the COPILOT permission; the watcher redirects them to dashboards. -->
    <div v-if="!denied" class="copilot-page-root">
        <TopNavBar :title="$t('ai.copilot.title')">
            <template #actions>
                <NavBarActions>
                    <NavBarAction
                        v-if="canCreateFlow"
                        :icon="Plus"
                        :label="$t('welcome_copilot.button_cta')"
                        :to="{name: 'flows/create'}"
                    />
                </NavBarActions>
            </template>
        </TopNavBar>

        <div class="copilot-page">
            <div class="copilot-page-surface">
                <CopilotChat layout="page" />
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import Plus from "vue-material-design-icons/Plus.vue"

    import CopilotChat from "./CopilotChat.vue"
    import TopNavBar from "../../layout/TopNavBar.vue"
    import NavBarActions from "../../layout/NavBarActions.vue"
    import NavBarAction from "../../layout/NavBarAction.vue"

    import useRouteContext from "../../../composables/useRouteContext"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../../models/resource"
    import action from "../../../models/action"

    const {t} = useI18n()
    useRouteContext(computed(() => ({title: t("ai.copilot.title")})))

    const route = useRoute()
    const router = useRouter()
    const authStore = useAuthStore()
    const canCreateFlow = computed(() => authStore.user?.hasAnyActionOnAnyNamespace(resource.FLOW, action.CREATE))

    // The copilot is gated by the backend's `@HasAnyResource(COPILOT)` and `/ai` is the post-login
    // landing, so a permission-less user can arrive here directly (redirect, typed URL, refresh) —
    // send them to their dashboards rather than strand them. Reactive (not a router guard) so it fires
    // once permissions finish loading and never misfires while loading; a no-op in OSS, where auth is
    // permissive.
    const denied = computed(() => !!authStore.user && !authStore.user.hasAny(resource.COPILOT))
    watch(
        denied,
        (value) => {
            if (value) router.replace({name: "home", params: {tenant: route.params.tenant}})
        },
        {immediate: true},
    )
</script>

<style scoped>
    .copilot-page-root {
        height: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }

    .copilot-page {
        flex: 1 1 auto;
        min-height: 0;
        display: flex;
        justify-content: center;
        padding: var(--ks-spacing-4);
    }

    /* Full-width so the transcript scroller spans the whole page and the wheel works from anywhere;
       CopilotChat's page layout centers the content into a bounded column itself. */
    .copilot-page-surface {
        width: 100%;
        min-height: 0;
        display: flex;
        flex-direction: column;
    }
</style>
