<script>
    import {ElNotification, ElTable, ElTableColumn} from "element-plus";
    import {h} from 'vue'

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
        notifications: [],
        watch: {
            $route() {
                this.notifications.forEach((item) => {
                    item.close()
                });
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
        render() {
            this.$nextTick(() => {
                const children = [
                    h('span', {innerHTML: this.text})
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
                            h(ElTableColumn, {prop: 'message', label: "Message"}),
                            h(ElTableColumn, {prop: 'path', label: "Path"}),
                        ]
                    ))
                }

                const current = ElNotification({
                    title: this.title || "Error",
                    message: h('div',  children),
                    type: "error",
                    duration: 0,
                    dangerouslyUseHTMLString: true,
                    customClass: "large"
                });

                if (this.notifications === undefined) {
                    this.notifications = [];
                }

                this.notifications.push(current);
            });
        }
    };
</script>

