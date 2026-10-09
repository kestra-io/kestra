<template>
    <div class="item">
        <div
            class="row"
            :class="{unread: !notification.read}"
            role="button"
            tabindex="0"
            @click="$emit('click')"
            @keydown.enter="$emit('click')"
            @keydown.space.prevent="$emit('click')"
        >
            <component
                :is="NOTIFICATION_ICONS[notification.type] ?? BellOutline"
                class="icon"
                aria-hidden="true"
            />
            <div class="content">
                <KsDateAgo
                    className="date"
                    :date="notification.createdDate"
                    format="L LTS"
                    inverted
                    :showTooltip="false"
                />
                <p class="title">{{ notification.title }}</p>
                <span
                    v-if="notification.referenceId"
                    class="reference"
                    :class="{link: isNotificationLinkable(notification.type)}"
                    data-test="reference"
                >
                    <KsId :value="notification.referenceId" />
                </span>
                <div v-if="notification.totalItems != null" class="progress">
                    <KsProgress
                        class="bar"
                        :percentage="progressPercentage(notification) ?? 0"
                        :color="progressColor(notification)"
                        :showText="false"
                        :strokeWidth="4"
                    />
                    <span>{{ notification.succeededItems ?? 0 }} / {{ notification.totalItems }}</span>
                </div>
            </div>
            <span v-if="!notification.read" class="dot" />
            <span class="actions">
                <KsIconButton
                    v-if="notification.read"
                    size="sm"
                    data-test="mark-unread"
                    :tooltip="$t('notifications.markUnread')"
                    @click.stop="$emit('mark-unread')"
                >
                    <EmailMarkAsUnread />
                </KsIconButton>
                <KsIconButton
                    v-else
                    size="sm"
                    data-test="mark-read"
                    :tooltip="$t('notifications.markRead')"
                    @click.stop="$emit('mark-read')"
                >
                    <Check />
                </KsIconButton>
            </span>
        </div>
    </div>
</template>

<script setup lang="ts">
    import BellOutline from "vue-material-design-icons/BellOutline.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import EmailMarkAsUnread from "vue-material-design-icons/EmailMarkAsUnread.vue"
    import type {Notification} from "../../stores/notifications"
    import {NOTIFICATION_ICONS} from "../../utils/notificationIcons"
    import {progressColor, progressPercentage} from "../../utils/notificationProgress"
    import {isNotificationLinkable} from "../../utils/notificationRoute"

    defineProps<{notification: Notification}>()
    defineEmits<{click: []; "mark-read": []; "mark-unread": []}>()
</script>

<style scoped lang="scss">
    .item {
        padding: var(--ks-spacing-1) 0;
    }

    .row {
        display: flex;
        align-items: flex-start;
        gap: var(--ks-spacing-3);
        margin: 0 var(--ks-spacing-4);
        padding: var(--ks-spacing-3);
        border: 1px solid transparent;
        border-radius: var(--ks-radius-lg);
        cursor: pointer;
        transition: background-color 0.15s ease, border-color 0.15s ease;

        &:hover,
        &:focus-visible {
            background: var(--ks-bg-hover-elevated);
            border-color: var(--ks-border-focus);
        }

        &:hover .actions,
        &:focus-within .actions {
            opacity: 1;
        }

        &.unread .title {
            font-weight: var(--ks-font-weight-semibold);
        }

        .icon {
            flex-shrink: 0;
            margin-top: 0.15rem;
            color: var(--ks-text-secondary);
        }

        .content {
            display: flex;
            flex: 1;
            flex-direction: column;
            gap: var(--ks-spacing-1);
            min-width: 0;

            :deep(.date) {
                font-size: var(--ks-font-size-2xs);
                font-weight: 400;
                color: var(--ks-text-secondary);
            }

            .title {
                margin: 0;
                font-size: var(--ks-font-size-md);
                color: var(--ks-text-primary);
            }

            .link :deep(code) {
                color: var(--ks-text-link);
            }

            .progress {
                display: flex;
                align-items: center;
                gap: var(--ks-spacing-2);
                font-size: var(--ks-font-size-2xs);
                color: var(--ks-text-secondary);

                .bar {
                    flex: 1;
                }
            }
        }

        .dot {
            flex-shrink: 0;
            width: 0.5rem;
            height: 0.5rem;
            margin-top: 0.4rem;
            border-radius: 50%;
            background-color: var(--ks-status-error);
        }

        .actions {
            flex-shrink: 0;
            opacity: 0;
            transition: opacity 0.15s ease;
        }
    }
</style>
