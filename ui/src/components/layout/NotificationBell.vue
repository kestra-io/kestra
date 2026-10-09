<template>
    <KsBadge
        class="bell"
        type="danger"
        :value="store.unreadCount"
        :max="9"
        :hidden="store.unreadCount === 0"
    >
        <KsIconButton
            class="toggle"
            :class="{pulsing: isRunning}"
            :ariaLabel="$t(isRunning ? 'notifications.bellAriaLabelRunning' : 'notifications.bellAriaLabel')"
            data-test="notification-bell"
            @click="toggle"
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
    const store = useNotificationsStore()

    const isRunning = computed(() => store.notifications.some(n => n.ongoing))

    /** Leaves lastContextTab alone: the bell is its own entry point and must not change what the dock toggle reopens. */
    function toggle() {
        miscStore.contextInfoBarOpenTab = miscStore.contextInfoBarOpenTab === "notifications" ? "" : "notifications"
    }

    /** Reads composedPath(), which keeps the click's ancestry even when the target detaches first (a row leaving the Unread tab once read). */
    function closeOnOutsideClick(event: MouseEvent) {
        if (miscStore.contextInfoBarOpenTab !== "notifications") return

        const inside = event.composedPath().some(el =>
            el instanceof HTMLElement && (el.classList.contains("contextDrawer") || el.classList.contains("bell")),
        )
        if (!inside) {
            miscStore.contextInfoBarOpenTab = ""
        }
    }

    onMounted(() => {
        store.startSSE()
        document.addEventListener("click", closeOnOutsideClick)
    })

    onUnmounted(() => {
        store.stopSSE()
        document.removeEventListener("click", closeOnOutsideClick)
    })
</script>

<style scoped lang="scss">
    .bell {
        --el-badge-size: 14px;
        --el-badge-font-size: 9px;
        --el-badge-padding: 4px;

        .toggle {
            color: var(--ks-icon-muted);

            :deep(svg) {
                fill: currentColor;
                stroke: currentColor;
            }

            &.pulsing {
                color: var(--ks-icon-active);
                animation: pulse 2s ease-in-out infinite;

                @media (prefers-reduced-motion: reduce) {
                    animation: none;
                }
            }
        }
    }

    @keyframes pulse {
        50% {
            opacity: 0.4;
        }
    }
</style>
