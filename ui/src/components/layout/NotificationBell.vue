<template>
    <KsBadge :value="unreadCount" :max="9" :hidden="unreadCount === 0" type="danger" class="bell-wrapper">
        <KsIconButton
            class="icon-btn"
            :ariaLabel="$t('notifications.bellAriaLabel')"
            @click="toggleNotifications"
        >
            <BellOutline />
        </KsIconButton>
    </KsBadge>
</template>

<script setup lang="ts">
    import {computed, onMounted, onUnmounted} from "vue"

    import BellOutline from "vue-material-design-icons/BellOutline.vue"

    import {useMiscStore} from "override/stores/misc"
    import {useNotificationsStore} from "../../stores/notifications"

    const miscStore = useMiscStore()
    const notificationsStore = useNotificationsStore()
    const unreadCount = computed(() => notificationsStore.unreadCount)

    // Never touches lastContextTab: the bell is its own entry point, not a tab strip item, so it must not hijack the shared dock-toggle's "reopen last tab" behavior.
    function toggleNotifications() {
        miscStore.contextInfoBarOpenTab = miscStore.contextInfoBarOpenTab === "notifications" ? "" : "notifications"
    }

    // Closes the panel on outside clicks, scoped to the "notifications" tab only.
    function onDocumentClick(event: MouseEvent) {
        if (miscStore.contextInfoBarOpenTab !== "notifications") return

        // composedPath() reflects the click's original ancestry even if the target detaches before this runs (e.g. a row leaving the Unread tab after being marked read).
        const insidePanel = event.composedPath().some(
            (el) => el instanceof HTMLElement && (el.classList.contains("contextDrawer") || el.classList.contains("bell-wrapper")),
        )
        if (insidePanel) return

        miscStore.contextInfoBarOpenTab = ""
    }

    // Mounted once via AppTopNavBar's panel-toggle slot, independent of the drawer, so the badge
    // and the SSE subscription both stay live app-wide from startup.
    onMounted(() => {
        notificationsStore.startSSE()
        document.addEventListener("click", onDocumentClick)
    })
    onUnmounted(() => {
        notificationsStore.stopSSE()
        document.removeEventListener("click", onDocumentClick)
    })
</script>

<style scoped lang="scss">
    .bell-wrapper {
        --el-badge-size: 14px;
        --el-badge-font-size: 9px;
        --el-badge-padding: 4px;
    }

    .icon-btn {
        color: var(--ks-icon-muted);

        &:deep(svg) {
            fill: currentColor;
            stroke: currentColor;
        }
    }
</style>
