<template>
    <!-- "Need Help?" section for the full-page copilot home (page layout only): quick links to
         Blueprints and the Slack community. Reuses the existing onboarding copy so no new i18n. -->
    <div class="copilot-help" data-test="copilot-help">
        <KsText size="small" class="copilot-help-title">{{ $t("welcome_copilot.need_help") }}</KsText>

        <RouterLink class="copilot-help-card" :to="{name: 'blueprints', params: {kind: 'flow', tab: 'community'}}">
            <KsIcon class="copilot-help-icon"><FileTreeOutline /></KsIcon>
            <span class="copilot-help-text">
                <KsText class="copilot-help-card-title">{{ $t("welcome_copilot.success_page.items.blueprints.title") }}</KsText>
                <KsText size="small" class="copilot-help-card-desc">{{ $t("welcome_copilot.success_page.items.blueprints.description") }}</KsText>
            </span>
            <KsIcon class="copilot-help-chevron"><ChevronRight /></KsIcon>
        </RouterLink>

        <a class="copilot-help-card" :href="SLACK_URL" target="_blank" rel="noopener noreferrer">
            <KsIcon class="copilot-help-icon"><Slack /></KsIcon>
            <span class="copilot-help-text">
                <KsText class="copilot-help-card-title">{{ $t("welcome_copilot.success_page.items.slack.title") }}</KsText>
                <KsText size="small" class="copilot-help-card-desc">{{ $t("welcome_copilot.success_page.items.slack.description") }}</KsText>
            </span>
            <KsIcon class="copilot-help-chevron"><ChevronRight /></KsIcon>
        </a>
    </div>
</template>

<script setup lang="ts">
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    const SLACK_URL = "https://kestra.io/slack"
</script>

<style scoped>
    .copilot-help {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;
        margin-top: var(--ks-spacing-4);
        /* The empty-state hero centers its content; the help section reads better left-aligned. */
        text-align: left;
    }

    .copilot-help-title {
        --kel-text-color: var(--ks-text-secondary);
        font-weight: 600;
        /* The KsText renders content-width in the flex column, so pin it to the left explicitly. */
        align-self: flex-start;
    }

    .copilot-help-card {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border: 1px solid var(--ks-border-subtle);
        border-radius: var(--ks-radius-lg);
        background: var(--ks-bg-surface);
        text-decoration: none;
        color: var(--ks-text-primary);
    }

    .copilot-help-card:hover {
        background: var(--ks-bg-hover);
        border-color: var(--ks-border-default);
    }

    .copilot-help-icon {
        flex: 0 0 auto;
        color: var(--ks-text-link);
    }

    .copilot-help-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1 1 auto;
        /* Left-align the title/description within the card (they render content-width, not stretched). */
        align-items: flex-start;
    }

    .copilot-help-card-title {
        font-weight: 600;
        align-self: flex-start;
    }

    .copilot-help-card-desc {
        --kel-text-color: var(--ks-text-secondary);
        align-self: flex-start;
    }

    .copilot-help-chevron {
        flex: 0 0 auto;
        color: var(--ks-text-secondary);
    }
</style>
