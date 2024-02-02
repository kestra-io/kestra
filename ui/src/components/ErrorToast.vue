<script>
    import {ElNotification, ElTable, ElTableColumn} from "element-plus";
    import Slack from "vue-material-design-icons/Slack.vue";
    import {pageFromRoute} from "../utils/eventsRouter";
    import {h} from "vue"

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
            text () {
                return this.message.message || this.message.content.message
            },
            title () {
                return this.message.title || "Error"
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
            }
        },
        render() {
            this.$nextTick(() => {
                this.close();

                const error =  {
                    type: "ERROR",
                    error: {
                        message: this.text,
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

                const children = [
                    h("a", {
                        href: "https://kestra.io/slack",
                        class: "position-absolute slack-on-error el-button el-button--small is-text is-has-bg",
                        target: "_blank"
                    }, [h(Slack), h("span", {innerText: this.$t("slack support")})]),
                    h("span", {innerHTML: this.text})
                ];

                if (this.items.length > 0) {
                    children.push(h(
                        ElTable,
                        {
                            stripe: true,
                            tableLayout: "auto",
                            fixed: true,
                            data: this.items,
                            class: ["mt-2"],
                            size: "small",
                        },
                        [
                            h(ElTableColumn, {prop: "message", label: "Message"}),
                            h(ElTableColumn, {prop: "path", label: "Path"}),
                        ]
                    ))
                }

                this.notifications = ElNotification({
                    title: this.title || "Error",
                    message: h("div",  children),
                    position: "bottom-right",
                    type: "error",
                    duration: 0,
                    dangerouslyUseHTMLString: true,
                    customClass: "error-notification" + (children.length > 1 ? " large" : "")
                });
            });

            return "";
        }
    };
</script>

<style lang="scss">
    .error-notification {
        .el-notification__title {
            max-width: calc(100% - 15ch);
        }

        .slack-on-error {
            top: calc(18px + 0.5rem);
            right: calc(15px + 2rem);
            transform: translateY(-50%);
            gap: calc(var(--spacer) / 2);
        }
    }
</style>