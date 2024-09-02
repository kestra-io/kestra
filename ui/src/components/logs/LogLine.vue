<template>
    <div class="line font-monospace" v-if="filtered">
        <span :class="levelClass" class="header-badge log-level el-tag noselect fw-bold">{{ log.level }}</span>
        <div class="log-content d-inline-block">
            <span v-if="title" class="fw-bold">{{ (log.taskId ?? log.flowId ?? "").capitalize() }}</span>
            <div
                class="header"
                :class="{'d-inline-block': metaWithValue.length === 0, 'me-3': metaWithValue.length === 0}"
            >
                <span class="header-badge">
                    {{ $filters.date(log.timestamp, "iso") }}
                </span>
                <span v-for="(meta, x) in metaWithValue" :key="x">
                    <span class="header-badge property">
                        <span>{{ meta.key }}</span>
                        <template v-if="meta.router">
                            <router-link :to="meta.router">{{ meta.value }}</router-link>
                        </template>
                        <template v-else>
                            {{ meta.value }}
                        </template>
                    </span>
                </span>
            </div>
            <v-runtime-template :template="markdownRenderer" />
        </div>
    </div>
</template>
<script>
    import Convert from "ansi-to-html"
    import xss from "xss";
    import Markdown from "../../utils/markdown";
    import VRuntimeTemplate from "vue3-runtime-template";

    let convert = new Convert();

    export default {
        components:{
            VRuntimeTemplate
        },
        props: {
            log: {
                type: Object,
                required: true,
            },
            filter: {
                type: String,
                default: "",
            },
            level: {
                type: String,
                required: true,
            },
            excludeMetas: {
                type: Array,
                default: () => [],
            },
            title: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                markdownRenderer: undefined
            }
        },
        async created() {
            this.markdownRenderer = await this.renderMarkdown();
        },
        computed: {
            metaWithValue() {
                const metaWithValue = [];
                const excludes = [
                    "message",
                    "timestamp",
                    "thread",
                    "taskRunId",
                    "level",
                    "index",
                    "attemptNumber"
                ];
                excludes.push.apply(excludes, this.excludeMetas);
                for (const key in this.log) {
                    if (this.log[key] && !excludes.includes(key)) {
                        let meta = {key, value: this.log[key]};
                        if (key === "executionId") {
                            meta["router"] = {
                                name: "executions/update", params: {
                                    namespace: this.log["namespace"],
                                    flowId: this.log["flowId"],
                                    id: this.log[key]
                                }
                            };
                        }

                        if (key === "namespace") {
                            meta["router"] = {name: "flows/list", query: {namespace: this.log[key]}};
                        }


                        if (key === "flowId") {
                            meta["router"] = {
                                name: "flows/update",
                                params: {namespace: this.log["namespace"], id: this.log[key]}
                            };
                        }

                        metaWithValue.push(meta);
                    }
                }
                return metaWithValue;
            },
            levelClass() {
                return {
                    TRACE: "",
                    DEBUG: "el-tag--info",
                    INFO: "el-tag--success",
                    WARN: "el-tag--warning",
                    ERROR: "el-tag--danger",
                }[this.log.level];
            },
            filtered() {
                return (
                    this.filter === "" || (
                        this.log.message &&
                        this.log.message.toLowerCase().includes(this.filter)
                    )
                );
            },
            message() {
                let logMessage = !this.log.message ? "" : convert.toHtml(xss(this.log.message, {
                    allowList: {"span": ["style"]}
                }));

                logMessage = logMessage.replaceAll(
                    /(['"]?)(https?:\/\/[^'"\s]+)(['"]?)/g,
                    "$1<a href='$2' target='_blank'>$2</a>$3"
                );
                return logMessage;
            }
        },
        methods: {
            async renderMarkdown() {
                let markdown = await Markdown.render(this.message);

                // Avoid rendering non-existent properties in the template by VRuntimeTemplate
                markdown = markdown.replace(/{{/g, "&#123;&#123;").replace(/}}/g, "&#125;&#125;");

                return markdown
            },
        },
    };
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    div.line {
        cursor: text;
        white-space: pre-wrap;
        word-break: break-all;
        display: flex;
        align-items: start;
        gap: $spacer;

        .log-level {
            padding: calc(var(--spacer) / 4);
        }

        .log-content {
            .header {
                color: var(--bs-gray-500);

                html.dark & {
                    color: var(--bs-gray-700);
                }

                > * + * {
                    margin-left: $spacer;
                }
            }
        }

        .el-tag {
            border-radius: 0;
            border: 0;
            height: auto;
        }

        .header-badge {
            font-size: 95%;
            text-align: center;
            white-space: nowrap;
            vertical-align: baseline;
            width: 40px;

            span:first-child {
                margin-right: 6px;
                font-family: var(--bs-font-sans-serif);
                user-select: none;

                &::after {
                    content: ":";
                }
            }

            & a {
                border-radius: var(--bs-border-radius);
            }

            &.log-level {
                white-space: pre;
                border-radius: 2px;
                color: $black;
            }
        }

        .noselect {
            user-select: none;
            color: $white;

            html:not(.dark) & {
                color: $black;
            }
        }

        .message {
            line-height: 1.8;
        }

        p, :deep(.log-content p) {
            display: inline;
            margin-bottom: 0;
        }
    }
</style>
