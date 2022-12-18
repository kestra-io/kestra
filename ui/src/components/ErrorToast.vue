<script>
    import {ElNotification, ElTable, ElTableColumn} from "element-plus";
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

                const children = [
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
                    type: "error",
                    duration: 0,
                    dangerouslyUseHTMLString: true,
                    customClass: children.length > 1 ? "large" : ""
                });
            });

            return "";
        }
    };
</script>

