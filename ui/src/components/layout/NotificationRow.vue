<template>
    <div class="row-slot">
        <div
            class="row"
            :class="{unread: !notification.read}"
            role="button"
            tabindex="0"
            @click="$emit('click')"
            @keydown.enter="$emit('click')"
            @keydown.space.prevent="$emit('click')"
        >
            <component :is="NOTIFICATION_ICONS[notification.type] ?? BellOutline" class="icon" aria-hidden="true" />
            <div class="metaBlock">
                <KsDateAgo className="notification-date" :inverted="true" :date="notification.createdDate" format="L LTS" :showTooltip="false" />
                <p class="title">{{ notification.title }}</p>
                <span v-if="isLinked" class="reference-link">
                    <KsId :value="notification.referenceId!" :shrink="true" />
                </span>
                <KsId
                    v-else-if="notification.referenceId"
                    :value="notification.referenceId"
                    :shrink="true"
                    class="reference-id-inert"
                />
                <div v-if="notification.totalItems != null" class="progress">
                    <progress :value="(notification.succeededItems) ?? 0 " :max="notification.totalItems" />
                    <span>{{ notification.succeededItems ?? 0 }} / {{ notification.totalItems }}</span>
                </div>
            </div>
            <span v-if="!notification.read" class="unreadDot" />
            <KsIconButton
                v-if="!notification.read"
                class="markReadBtn"
                size="sm"
                :tooltip="$t('notifications.markRead')"
                @click.stop="$emit('mark-read')"
            >
                <Check />
            </KsIconButton>
            <KsIconButton
                v-if="notification.read"
                class="markUnreadBtn"
                size="sm"
                :tooltip="$t('notifications.markUnread')"
                @click.stop="$emit('mark-unread')"
            >
                <EmailMarkAsUnread />
            </KsIconButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"

    import BellOutline from "vue-material-design-icons/BellOutline.vue"
    import Check from "vue-material-design-icons/Check.vue"
    import EmailMarkAsUnread from "vue-material-design-icons/EmailMarkAsUnread.vue"

    import {type Notification} from "../../stores/notifications"
    import {isNotificationLinkable} from "../../utils/notificationRoute"
    import {NOTIFICATION_ICONS} from "../../utils/notificationIcons"

    const props = defineProps<{notification: Notification}>()
    defineEmits<{click: []; "mark-read": []; "mark-unread": []}>()

    const isLinked = computed(() => props.notification.referenceId !== null && isNotificationLinkable(props.notification.type))
</script>

<style scoped lang="scss">
    // The vertical gap between rows lives here as padding, not as margin on .row: vue-virtual-scroller
    // measures each item's rendered box (getBoundingClientRect), which excludes margin - a margin-based
    // gap made the measured height ~4px short of the row's real footprint, clipping its hover border.
    .row-slot {
        padding: 0.25rem 0;
    }

    .row {
        position: relative;
        display: flex;
        gap: 0.75rem;
        align-items: flex-start;
        width: calc(100% - 2rem);
        margin: 0 1rem;
        padding: 0.75rem;
        border: 1px solid transparent;
        border-radius: 8px;
        background: none;
        color: inherit;
        text-align: left;
        cursor: pointer;
        transition: background-color 0.15s ease, border-color 0.15s ease;

        &:hover,
        &:focus-visible {
            background: var(--ks-bg-hover-elevated);
            border-color: var(--ks-border-focus);
        }

        &.unread .title {
            font-weight: 600;
        }
    }

    .icon {
        color: var(--ks-text-secondary);
        flex-shrink: 0;
        margin-top: 0.15rem;
    }

    .metaBlock {
        display: flex;
        flex-direction: column;
        gap: 0.25rem;
        min-width: 0;
        flex: 1;
    }

    .title {
        margin: 0;
        font-size: var(--ks-font-size-md);
        color: var(--ks-text-primary);
    }

    :deep(.reference-link) code {
        color: var(--ks-text-link);
    }

    .progress {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        font-size: var(--ks-font-size-2xs);
        color: var(--ks-text-secondary);

        progress {
            flex: 1;
        }
    }

    .unreadDot {
        width: 8px;
        height: 8px;
        background-color: var(--ks-status-error);
        border-radius: 50%;
        flex-shrink: 0;
        margin-top: 0.4rem;
    }

    .markUnreadBtn,
    .markReadBtn {
        flex-shrink: 0;
        color: var(--ks-icon-muted);
        opacity: 0;
        transition: opacity 0.15s ease;
    }

    .row:hover .markUnreadBtn,
    .row:hover .markReadBtn,
    .row:focus-within .markUnreadBtn,
    .row:focus-within .markReadBtn {
        opacity: 1;
    }

    :deep(.notification-date) {
        font-size: var(--ks-font-size-2xs);
        font-weight: 400;
        color: var(--ks-text-secondary);
    }
</style>
