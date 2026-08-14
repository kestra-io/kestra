<template>
    <div class="notify-grid" data-test="recipe-notify-grid">
        <SelectableTile
            v-for="channel in channels"
            :key="channel.key"
            role="checkbox"
            layout="column"
            :selected="recipe.notify[channel.key]"
            :disabled="!channelAvailability[channel.key]"
            :ariaLabel="channel.label"
            @select="toggleNotify(channel.key)"
        >
            <div class="card-header">
                <div class="icon-wrap">
                    <KsIcon class="channel-icon">
                        <component :is="channel.icon" />
                    </KsIcon>
                </div>
                <KsIcon class="check-indicator" aria-hidden="true">
                    <component :is="recipe.notify[channel.key] ? CheckboxMarked : CheckboxBlankOutline" />
                </KsIcon>
            </div>
            <span class="channel-label">{{ channel.label }}</span>
            <span class="channel-sub">{{ channel.sub }}</span>
            <span v-if="!channelAvailability[channel.key]" class="unavailable-note">
                {{ $t("recipe.notify.plugin_unavailable") }}
            </span>

            <template #config>
                <KsInput
                    v-if="channel.key === 'slack' && recipe.triggerType === 'execution'"
                    v-model="recipe.slackChannel"
                    :placeholder="$t('recipe.notify.slack_channel_placeholder')"
                    size="small"
                    data-test="recipe-slack-channel"
                />
                <KsInput
                    v-else-if="channel.key === 'teams'"
                    v-model="recipe.teamsWebhook"
                    :placeholder="$t('recipe.notify.teams_webhook_placeholder')"
                    size="small"
                    data-test="recipe-teams-webhook"
                />
                <KsInput
                    v-else-if="channel.key === 'email'"
                    v-model="recipe.emailTo"
                    :placeholder="$t('recipe.notify.email_to_placeholder')"
                    size="small"
                    data-test="recipe-email-to"
                />
                <span v-else-if="channel.key === 'custom'" class="custom-note">
                    {{ $t("recipe.notify.custom_note") }}
                </span>
            </template>
        </SelectableTile>
    </div>
</template>

<script setup lang="ts">
    import {computed, type Component} from "vue"
    import {useI18n} from "vue-i18n"
    import type {NotifyChannel, RecipeState} from "../../../composables/useFlowRecipe"
    import SelectableTile from "./SelectableTile.vue"

    import Slack from "vue-material-design-icons/Slack.vue"
    import MicrosoftTeams from "vue-material-design-icons/MicrosoftTeams.vue"
    import EmailOutline from "vue-material-design-icons/EmailOutline.vue"
    import DotsHorizontal from "vue-material-design-icons/DotsHorizontal.vue"
    import CheckboxMarked from "vue-material-design-icons/CheckboxMarked.vue"
    import CheckboxBlankOutline from "vue-material-design-icons/CheckboxBlankOutline.vue"

    defineProps<{
        recipe: RecipeState
        channelAvailability: Record<NotifyChannel, boolean>
        toggleNotify: (key: NotifyChannel) => void
    }>()

    const {t} = useI18n()

    const channels = computed<{key: NotifyChannel; label: string; sub: string; icon: Component}[]>(() => [
        {
            key: "slack",
            label: "Slack",
            sub: t("recipe.notify.slack_sub"),
            icon: Slack,
        },
        {
            key: "teams",
            label: "Microsoft Teams",
            sub: t("recipe.notify.teams_sub"),
            icon: MicrosoftTeams,
        },
        {
            key: "email",
            label: t("recipe.notify.email_label"),
            sub: t("recipe.notify.email_sub"),
            icon: EmailOutline,
        },
        {
            key: "custom",
            label: t("recipe.notify.custom_label"),
            sub: t("recipe.notify.custom_sub"),
            icon: DotsHorizontal,
        },
    ])
</script>

<style scoped lang="scss">
    .notify-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(min(14rem, 100%), 1fr));
        gap: var(--ks-spacing-3);
    }

    .card-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .icon-wrap {
        width: var(--ks-spacing-6);
        height: var(--ks-spacing-6);
        display: flex;
        align-items: center;
        justify-content: center;
        border-radius: var(--ks-radius-sm);
        background-color: var(--ks-bg-tag);
    }

    .channel-icon {
        color: var(--ks-text-primary);
    }

    .check-indicator {
        color: var(--ks-icon-muted);
    }

    .channel-label {
        display: block;
        font-weight: var(--ks-font-weight-medium);
    }

    .channel-sub {
        display: block;
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }

    .unavailable-note {
        display: block;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-warning);
    }

    .custom-note {
        display: block;
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
    }
</style>
