<template>
    <!-- "Need Help?" section for the full-page copilot home (page layout only): quick links to
         the product tour, Blueprints and the Slack community. Reuses the existing onboarding copy so no new i18n. -->
    <div class="copilot-help" data-test="copilot-help">
        <KsText size="small" class="title">{{ $t("welcome_copilot.need_help") }}</KsText>

        <div class="list">
            <OverviewCard
                :title="$t('onboarding.tour.menu')"
                :description="$t('onboarding.tour.menu_description')"
                :icon="Play"
                :to="{name: 'ai', query: {tour: 'start'}}"
            />
            <OverviewCard
                :title="$t('welcome_copilot.success_page.items.blueprints.title')"
                :description="$t('welcome_copilot.success_page.items.blueprints.description')"
                :icon="FileTreeOutline"
                :to="{name: 'blueprints', params: {kind: 'flow', tab: 'community'}}"
            />
            <OverviewCard
                :title="$t('welcome_copilot.success_page.items.slack.title')"
                :description="$t('welcome_copilot.success_page.items.slack.description')"
                :icon="Slack"
                :link="SLACK_URL"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import FileTreeOutline from "vue-material-design-icons/FileTreeOutline.vue"
    import Play from "vue-material-design-icons/Play.vue"
    import Slack from "vue-material-design-icons/Slack.vue"
    import OverviewCard from "../../onboarding/execution/OverviewCard.vue"

    const SLACK_URL = "https://kestra.io/slack"
</script>

<style scoped lang="scss">
    .copilot-help {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;
        margin: var(--ks-spacing-4) 0 var(--ks-spacing-4) 0;
        /* The empty-state hero centers its content; the help section reads better left-aligned. */
        text-align: left;

        .title {
            --kel-text-color: var(--ks-text-secondary);
            font-weight: var(--ks-font-weight-semibold);
            align-self: flex-start;
        }

        .list {
            display: flex;
            flex-direction: column;
            border: var(--ks-border-block-primary);
            border-radius: var(--ks-radius-base);
            overflow: hidden;

            > :not(:first-child) {
                border-top: var(--ks-border-block-primary);
            }
        }
    }
</style>
