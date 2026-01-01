
<script setup lang="ts">
    import {ElNotification} from "element-plus";
    import {pageFromRoute} from "../utils/eventsRouter";
    import {h, onMounted, watch, computed, ref} from "vue";
    import ErrorToastContainer from "./ErrorToastContainer.vue";
    import {useApiStore} from "../stores/api";
    import {useRoute} from "vue-router";

    export interface Message {
        title?: string;
        message?: string;
        content?: {
            message?: string;
            _embedded?: {
                errors?: any[];
            };
        };
        response?: {
            status: number;
            config: {
                url?: string;
                method?: string;
            };
        };
        variant?: "success" | "warning" | "info" | "error" | "primary";
    }

    interface ErrorEvent {
        type: string;
        error: {
            message: string;
            errors: any[];
            response?: {
                status?: number;
            };
            request?: {
                url: string;
                method: string;
            };
        };
        page: any;
    }

    const props = withDefaults(defineProps<{
        message: Message;
        noAutoHide: boolean;
    }>(), {
        noAutoHide: false
    });

    const route = useRoute();
    const apiStore = useApiStore();
    const notifications = ref<any>();

    const close = () => {
        if (notifications.value) {
            notifications.value.close();
        }
    };

    const title = computed(() => {
        if (props.message.title) {
            return props.message.title;
        }

        if (props.message.response?.status === 503) {
            return "503 Service Unavailable";
        }

        if (props.message.content?.message && props.message.content.message.indexOf(":") > 0) {
            return props.message.content.message.substring(0, props.message.content.message.indexOf(":"));
        }

        return "Error";
    });

    const items = computed(() => {
        const messages = props.message.content?._embedded?.errors || [];
        return Array.isArray(messages) ? messages : [messages];
    });

    watch(route, () => {
        close();
    });

    onMounted(() => {
        const error: ErrorEvent = {
            type: "ERROR",
            error: {
                message: title.value,
                errors: items.value,
            },
            page: pageFromRoute(route)
        };

        if (props.message.response) {
            error.error.response = {};
            error.error.request = {
                method: props.message.response.config.method ?? "GET",
                url: props.message.response.config.url ?? "unknown url",
            };

            if (props.message.response.status) {
                error.error.response.status = props.message.response.status;
            }
        }

        apiStore.events(error);

        notifications.value = ElNotification({
            title: title.value || "Error",
            message: h(ErrorToastContainer, {
                message: {
                    content:{
                        message: props.message?.content?.message ?? ""
                    }
                },
                items: items.value,
                onClose: () => close()
            }),
            position: "bottom-right",
            type: props.message.variant || "error",
            duration: 0,
            dangerouslyUseHTMLString: true,
            customClass: "error-notification large"
        });
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
            gap: .5rem;
        }

        .el-notification__content {
            overflow-y: auto;
            max-height: 100%;
        }
    }
</style>