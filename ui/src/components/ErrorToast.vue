<template>
    <!-- root element required by vue/valid-template-root.
       kept invisible because this component only shows ElementPlus notifications -->
    <div style="display: none" aria-hidden="true" />
</template>

<script setup lang="ts">
    import {ref, nextTick, watch, onBeforeUnmount} from "vue";
    import {useRoute} from "vue-router";
    import {ElNotification} from "element-plus";
    import {h} from "vue";
    import ErrorToastContainer from "./ErrorToastContainer.vue";
    import {useApiStore} from "../stores/api";
    import {pageFromRoute} from "../utils/eventsRouter";

    /** Minimal message typing — adjust if you want stricter types */
    type ErrorMessage = {
        title?: string;
        response?: any;
        content?: any;
        variant?: "success" | "warning" | "info" | "error" | string;
    };

    const props = defineProps<{
        message: ErrorMessage | null;
        noAutoHide?: boolean;
    }>();

    const apiStore = useApiStore();
    const route = useRoute();
    const notificationRef = ref<any | null>(null);

    function closeNotification() {
        if (notificationRef.value && typeof notificationRef.value.close === "function") {
            try {
                notificationRef.value.close();
            } catch {
                // ignore closing errors
            }
        }
        notificationRef.value = null;
    }

    function deriveTitle(message: ErrorMessage | null): string {
        if (!message) return "Error";
        if (message.title) return message.title;
        if (message.response && message.response.status === 503) {
            return "503 Service Unavailable";
        }
        const contentMsg = message.content && (message.content.message ?? "");
        if (typeof contentMsg === "string" && contentMsg.indexOf(":") > 0) {
            return contentMsg.substring(0, contentMsg.indexOf(":"));
        }
        return "Error";
    }

    function deriveItems(message: ErrorMessage | null) {
        const messages =
            message?.content && message.content._embedded && message.content._embedded.errors
                ? message.content._embedded.errors
                : [];
        return Array.isArray(messages) ? messages : [messages];
    }

    /* watch message -> create notification (mirrors original $nextTick render behaviour) */
    watch(
        () => props.message,
        async (newMsg) => {
            closeNotification();
            if (!newMsg) return;

            await nextTick();

            const errorEvent: any = {
                type: "ERROR",
                error: {
                    message: deriveTitle(newMsg),
                    errors: deriveItems(newMsg),
                },
                page: pageFromRoute(route),
            };

            if (newMsg.response) {
                errorEvent.error.response = {};
                errorEvent.error.request = {};

                if (newMsg.response.status) {
                    errorEvent.error.response.status = newMsg.response.status;
                }

                const cfg = newMsg.response.config ?? {};
                errorEvent.error.request.url = cfg.url;
                errorEvent.error.request.method = cfg.method;
            }

            apiStore.events(errorEvent);

            notificationRef.value = ElNotification({
                title: deriveTitle(newMsg) || "Error",
                message: h(ErrorToastContainer, {
                    message: newMsg,
                    items: deriveItems(newMsg),
                    onClose: () => closeNotification(),
                }),
                position: "bottom-right",
                type: newMsg.variant ?? "error",
                duration: props.noAutoHide ? 0 : 0,
                dangerouslyUseHTMLString: true,
                customClass: "error-notification large",
            });
        },
        {immediate: true}
    );

    /* close on route change */
    watch(
        () => route.fullPath,
        () => {
            closeNotification();
        }
    );

    onBeforeUnmount(() => {
        closeNotification();
    });
</script>

<style lang="scss" scoped>
.error-notification {
  max-height: 90svh;

  .el-notification__title {
    max-width: calc(100% - 15ch);
  }

  .slack-on-error {
    top: calc(18px + 0.5rem);
    right: calc(15px + 2rem);
    transform: translateY(-50%);
    gap: 0.5rem;
  }

  .el-notification__content {
    overflow-y: auto;
    max-height: 100%;
  }
}
</style>
