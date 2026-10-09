<template>
    <ContextInfoContent ref="content">
        <header class="header">
            <h4>{{ $t("notifications.heading") }}</h4>
            <KsButton
                v-if="store.unreadCount > 0"
                size="small"
                link
                :icon="CheckAll"
                @click="store.markAllRead()"
            >
                {{ $t("notifications.markAllRead") }}
            </KsButton>
        </header>

        <section v-if="ongoing.length > 0" class="ongoing">
            <h5>
                {{ $t("notifications.ongoing") }}
                <KsBadge :value="ongoing.length" type="info" inline />
            </h5>
            <div class="operations">
                <OperationCard
                    v-for="notification in ongoing"
                    :key="notification.id"
                    :notification="notification"
                    @click="open(notification)"
                />
            </div>
        </section>

        <KsTabs v-model="activeTab" type="segmented" class="tabs">
            <KsTabPane name="all" :label="$t('notifications.tabs.all')" />
            <KsTabPane name="unread" :label="$t('notifications.tabs.unread')" />
        </KsTabs>

        <p v-if="visible.length === 0" class="empty">
            {{ $t(activeTab === "unread" ? "notifications.emptyUnread" : "notifications.empty") }}
        </p>

        <DynamicScroller
            v-else
            :items="visible"
            :minItemSize="72"
            keyField="id"
            :prerender="30"
            pageMode
            emitUpdate
            :scrollParent="scrollParent ?? undefined"
            @scroll-end="store.loadMore()"
        >
            <template #default="{item, active}">
                <DynamicScrollerItem
                    :key="item.id"
                    :item="item"
                    :active="active"
                    :sizeDependencies="[item.title, item.read, item.referenceId, item.totalItems]"
                    :data-index="item.id"
                >
                    <NotificationRow
                        :notification="item"
                        @click="open(item)"
                        @mark-read="setRead(item, true)"
                        @mark-unread="setRead(item, false)"
                    />
                </DynamicScrollerItem>
            </template>
        </DynamicScroller>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref, useTemplateRef} from "vue"
    import {useRouter} from "vue-router"
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller"
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import CheckAll from "vue-material-design-icons/CheckAll.vue"
    import {useMiscStore} from "override/stores/misc"
    import {beforeNotificationNavigation} from "override/utils/notificationNavigation"
    import ContextInfoContent from "../ContextInfoContent.vue"
    import NotificationRow from "./NotificationRow.vue"
    import OperationCard from "./OperationCard.vue"
    import {useScrollMemory} from "../../composables/useScrollMemory"
    import {useNotificationsStore, type Notification} from "../../stores/notifications"
    import {resolveNotificationRoute} from "../../utils/notificationRoute"

    const store = useNotificationsStore()
    const miscStore = useMiscStore()
    const router = useRouter()
    const content = useTemplateRef<InstanceType<typeof ContextInfoContent>>("content")

    const activeTab = ref("all")
    const ongoing = computed(() => store.notifications.filter(n => n.ongoing))
    /** Newest first in both tabs: grouping unread rows first would reorder them as reads land mid-scroll. */
    const visible = computed(() =>
        store.notifications.filter(n => !n.ongoing && (activeTab.value === "all" || !n.read)),
    )
    const scrollParent = computed(() => content.value?.contentRef ?? null)

    useScrollMemory(ref("context-panel-notifications"), scrollParent)

    onMounted(() => store.loadInitial())

    /** The store reverts its optimistic flag on failure, which is all the feedback the user needs. */
    async function setRead(notification: Notification, read: boolean) {
        if (!notification.id) return

        await (read ? store.markRead : store.markUnread)(notification.id).catch(() => undefined)
    }

    async function open(notification: Notification) {
        if (!notification.read) {
            await setRead(notification, true)
        }

        const route = await resolveNotificationRoute(notification)
        if (route === null) return

        await beforeNotificationNavigation(notification)
        miscStore.contextInfoBarOpenTab = ""
        router.push({
            ...route,
            params: {tenant: notification.tenantId ?? undefined, ...route.params},
        })
    }
</script>

<style scoped lang="scss">
    .header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: var(--ks-spacing-6) var(--ks-spacing-6) 0;

        h4 {
            margin: 0;
            font-size: var(--ks-font-size-md);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-primary);
        }
    }

    .ongoing {
        margin-top: var(--ks-spacing-4);

        h5 {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            margin: 0 var(--ks-spacing-6);
            font-size: var(--ks-font-size-2xs);
            font-weight: var(--ks-font-weight-semibold);
            text-transform: uppercase;
            color: var(--ks-text-secondary);
        }

        .operations {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-2);
            margin: var(--ks-spacing-2) var(--ks-spacing-4) 0;
        }
    }

    .tabs {
        margin: var(--ks-spacing-4) var(--ks-spacing-6) 0;
    }

    .empty {
        padding: var(--ks-spacing-4) var(--ks-spacing-6) var(--ks-spacing-6);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-secondary);
    }
</style>
