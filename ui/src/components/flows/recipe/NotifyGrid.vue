<template>
    <div class="notify-grid" data-test="recipe-notify-grid">
        <div
            v-for="channel in channels"
            :key="channel.key"
            class="notify-card"
            :class="{
                selected: recipe.notify[channel.key as keyof typeof recipe.notify],
                unavailable: !channelAvailability[channel.key as keyof typeof channelAvailability],
            }"
            role="checkbox"
            :aria-checked="recipe.notify[channel.key as keyof typeof recipe.notify]"
            :aria-disabled="!channelAvailability[channel.key as keyof typeof channelAvailability]"
            :aria-label="channel.label"
            :tabindex="channelAvailability[channel.key as keyof typeof channelAvailability] ? 0 : -1"
            @click="channelAvailability[channel.key as keyof typeof channelAvailability] && toggleNotify(channel.key as keyof typeof recipe.notify)"
            @keydown.enter="channelAvailability[channel.key as keyof typeof channelAvailability] && toggleNotify(channel.key as keyof typeof recipe.notify)"
            @keydown.space.prevent="channelAvailability[channel.key as keyof typeof channelAvailability] && toggleNotify(channel.key as keyof typeof recipe.notify)"
        >
            <div class="card-header">
                <div class="icon-wrap">
                    <KsIcon class="channel-icon">
                        <component :is="channel.icon" />
                    </KsIcon>
                </div>
                <KsCheckbox
                    :modelValue="recipe.notify[channel.key as keyof typeof recipe.notify]"
                    class="checkbox-passive"
                    :aria-hidden="true"
                />
            </div>
            <span class="channel-label">{{ channel.label }}</span>
            <span class="channel-sub">{{ channel.sub }}</span>
            <span v-if="!channelAvailability[channel.key as keyof typeof channelAvailability]" class="unavailable-note">
                {{ $t("recipe.notify.plugin_unavailable") }}
            </span>

            <div v-if="recipe.notify[channel.key as keyof typeof recipe.notify]" class="channel-config" @click.stop>
                <KsInput
                    v-if="channel.key === 'slack'"
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
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {useI18n} from "vue-i18n"
    import type {RecipeState} from "../../../composables/useFlowRecipe"

    import Slack from "vue-material-design-icons/Slack.vue"
    import MicrosoftTeams from "vue-material-design-icons/MicrosoftTeams.vue"
    import EmailOutline from "vue-material-design-icons/EmailOutline.vue"

    const props = defineProps<{
        recipe: RecipeState
        channelAvailability: {slack: boolean; teams: boolean; email: boolean}
        toggleNotify: (key: keyof RecipeState["notify"]) => void
    }>()

    const {t} = useI18n()

    const channels = [
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
    ]
</script>

<style scoped lang="scss">
    .notify-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(14rem, 1fr));
        gap: var(--ks-spacing-3);
    }

    .notify-card {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-3);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        transition: border-color 0.15s, background-color 0.15s;

        &:hover:not(.unavailable) {
            border-color: var(--ks-border-strong);
            background-color: var(--ks-bg-hover);
        }

        &.selected {
            border-color: var(--ks-border-focus);
            background-color: var(--ks-bg-tag-active);
        }

        &.unavailable {
            opacity: 0.5;
            cursor: not-allowed;
        }
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

    .checkbox-passive {
        pointer-events: none;
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

    .channel-config {
        margin-top: var(--ks-spacing-1);
    }
</style>
