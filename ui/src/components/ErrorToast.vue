<script>
    import {ElNotification} from "element-plus";
    import {pageFromRoute} from "../utils/eventsRouter";
    import {h} from "vue"
    import ErrorToastContainer from "./ErrorToastContainer.vue";

    export default {
        name: "ErrorToast",
        props: {
            message: {
                type: Object,
                required: true
            },
            noAutoHide: {
                type: Boolean,
                default: false
            }
        },
        notifications: undefined,
        watch: {
            $route() {
                this.close();
            },
        },
        computed: {
            title () {
                if (this.message.title) {
                    return this.message.title;
                }

                if (this.message.response.status === 503) {
                    return "503 Service Unavailable";
                }

                if (this.message.content && this.message.content.message && this.message.content.message.indexOf(":") > 0) {
                    return this.message.content.message.substring(0, this.message.content.message.indexOf(":"));
                }

                return "Error"
            },
            items() {
                const messages = this.message.content && this.message.content._embedded && this.message.content._embedded.errors ? this.message.content._embedded.errors : []
                return Array.isArray(messages) ? messages : [messages]
            },
        },
        methods: {
            close() {
                if (this.notifications) {
                    this.notifications.close();
                }
            },
        },
        render() {
            this.$nextTick(async () => {
                this.close();

                const error =  {
                    type: "ERROR",
                    error: {
                        message: this.title,
                        errors: this.items,
                    },
                    page: pageFromRoute(this.$route)
                };

                if (this.message.response) {
                    error.error.response = {};
                    error.error.request = {};

                    if (this.message.response.status) {
                        error.error.response.status = this.message.response.status;
                    }

                    error.error.request.url = this.message.response.config.url;
                    error.error.request.method = this.message.response.config.method;
                }

                this.$store.dispatch("api/events", error);

                this.notifications = ElNotification({
                    title: this.title || "Error",
                    message: h(ErrorToastContainer, {message: this.message, items: this.items}),
                    position: "bottom-right",
                    type: this.message.variant,
                    duration: 0,
                    dangerouslyUseHTMLString: true,
                    customClass: "error-notification large"
                });
            });

            return "";
        }
    };
</script>

<style lang="scss">
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