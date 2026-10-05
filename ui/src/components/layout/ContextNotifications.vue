<template>
    <ContextInfoContent ref="contextInfoRef">
        <header class="notificationsHeader">
            <h4>{{ $t("notifications.heading") }}</h4>
            <KsButton
                v-if="store.unreadCount > 0"
                size="small"
                text
                :icon="CheckAll"
                @click="store.markAllRead()"
            >
                {{ $t("notifications.markAllRead") }}
            </KsButton>
        </header>

        <section v-if="store.hasOngoingOperation" class="ongoingSection">
            <h5 class="ongoingHeading">
                {{ $t("notifications.ongoing") }}
                <KsBadge :value="store.ongoingOperations.length" type="info" inline />
            </h5>
            <NotificationRow
                v-for="notification in store.ongoingOperations"
                :key="notification.id"
                :notification="notification"
                @click="onRowClick(notification)"
                @mark-read="onMarkReadClick(notification)"
                @mark-unread="onMarkUnreadClick(notification)"
            />
        </section>

        <KsSegmented v-model="activeTab" :options="tabOptions" size="small" class="tabs" />

        <p v-if="visibleNotifications.length === 0" class="empty">
            {{ activeTab === "unread" ? $t("notifications.emptyUnread") : $t("notifications.empty") }}
        </p>

        <DynamicScroller
            v-else
            :items="visibleNotifications"
            :minItemSize="72"
            keyField="id"
            :prerender="30"
            pageMode
            emitUpdate
            :scrollParent="scrollableElement ?? undefined"
            @scroll-end="store.loadMore()"
        >
            <template #default="{item, active}">
                <DynamicScrollerItem
                    :item="asNotification(item)"
                    :active="active"
                    :sizeDependencies="[asNotification(item).title, asNotification(item).read, asNotification(item).referenceId, asNotification(item).totalItems]"
                    :data-index="asNotification(item).id"
                    :key="asNotification(item).id"
                >
                    <NotificationRow
                        :notification="asNotification(item)"
                        @click="onRowClick(asNotification(item))"
                        @mark-read="onMarkReadClick(asNotification(item))"
                        @mark-unread="onMarkUnreadClick(asNotification(item))"
                    />
                </DynamicScrollerItem>
            </template>
        </DynamicScroller>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"

    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"

    import CheckAll from "vue-material-design-icons/CheckAll.vue"

    import {useScrollMemory} from "../../composables/useScrollMemory"
    import ContextInfoContent from "../ContextInfoContent.vue"
    import {useMiscStore} from "override/stores/misc"

    import NotificationRow from "./NotificationRow.vue"
    import {useNotificationsStore, type Notification} from "../../stores/notifications"
    import {resolveNotificationRoute, type NotificationRoute} from "../../utils/notificationRoute"

    const store = useNotificationsStore()
    const router = useRouter()
    const miscStore = useMiscStore()
    const {t} = useI18n()

    const activeTab = ref<"all" | "unread">("all")
    const tabOptions = computed(() => [
        {label: t("notifications.tabs.all"), value: "all"},
        {label: t("notifications.tabs.unread"), value: "unread"},
    ])
    // Plain publicationDate-desc order in both tabs — the store already keeps `notifications`
    // sorted newest-first. No unread-first grouping: that would re-order rows as reads land
    // mid-scroll and make the infinite-scroll list stutter.
    // Sourced from staticNotifications: ongoing ones already have their own section above.
    const visibleNotifications = computed(() => activeTab.value === "unread"
        ? store.staticNotifications.filter(n => !n.read)
        : store.staticNotifications)

    const contextInfoRef = ref<InstanceType<typeof ContextInfoContent> | null>(null)

    // DynamicScroller's default slot loses the item's TS type (same cast Logs.vue applies via
    // its asLog() helper) — this is a plain type-narrowing cast, not a computation.
    function asNotification(item: unknown): Notification {
        return item as Notification
    }

    onMounted(async () => {
        await store.loadInitial()
    })

    // The whole row is the click target — not just the referenceId — so a single click both
    // marks the notification read and navigates to it, matching how the rest of the row looks
    // (one clickable card, not a card with a smaller link inside it).
    async function onRowClick(notification: Notification) {
        if (!notification.read && notification.id) {
            // markRead() already reverts the optimistic flag + restores the badge on failure,
            // which is the user-visible signal — nothing else to surface here.
            try {
                await store.markRead(notification.id)
            } catch {
                // handled by the store's revert; avoid an unhandled rejection
            }
        }

        await navigateTo(notification)
    }

    async function onMarkUnreadClick(notification: Notification) {
        if (!notification.id) return
        try {
            await store.markUnread(notification.id)
        } catch {
            // handled by the store's revert; avoid an unhandled rejection
        }
    }

    async function onMarkReadClick(notification: Notification) {
        if (!notification.id) return
        try {
            await store.markRead(notification.id)
        } catch {
            // handled by the store's revert; avoid an unhandled rejection
        }
    }

    // Null whenever resolveNotificationRoute has nothing to offer (no referenceId, or no
    // producer registered for the notification's type) — the template falls back to an
    // inert KsId in that case instead of styling the referenceId as a link.
    async function referenceRouteTo(notification: Notification): Promise<NotificationRoute | null> {
        const route = await resolveNotificationRoute(notification)
        if (route === null) {
            return null
        }

        return {
            name: route.name,
            params: {tenant: notification.tenantId ?? undefined, ...route.params},
            ...(route.query ? {query: route.query} : {}),
        }
    }

    // No-op whenever referenceRouteTo() has nothing to offer.
    async function navigateTo(notification: Notification) {
        const route = await referenceRouteTo(notification)
        if (route === null) {
            return
        }

        // Clicking through to the referenced page is the whole point of the click — staying
        // parked on top of the new page serves no purpose, so close the panel same as an
        // explicit outside-click would.
        miscStore.contextInfoBarOpenTab = ""
        router.push(route)
    }

    const scrollableElement = computed(() => contextInfoRef.value?.contentRef || null)
    useScrollMemory(ref("context-panel-notifications"), scrollableElement)
</script>

<style scoped lang="scss">
    .notificationsHeader {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 2rem 2rem 0;

        h4 {
            font-size: var(--ks-font-size-md);
            font-weight: 600;
            margin: 0;
            color: var(--ks-text-primary);
        }
    }

    .ongoingSection {
        margin-top: 1rem;
    }

    .ongoingHeading {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin: 0 2rem;
        font-size: var(--ks-font-size-2xs);
        font-weight: 600;
        text-transform: uppercase;
        color: var(--ks-text-secondary);
    }

    .tabs {
        margin: 1rem 2rem 0;
    }

    .empty {
        padding: 1rem 2rem 2rem;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }
</style>
