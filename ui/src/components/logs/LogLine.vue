<template>
    <div
        class="py-2 line font-monospace"
        :class="{['log-border-' + log.level.toLowerCase()]: cursor && log.level !== undefined, ['key-' + $.vnode.key]: true}"
        v-if="filtered"
        :style="logLineStyle"
    >
        <el-icon v-if="cursor" class="icon_container" :style="{color: iconColor}" :size="25">
            <MenuRight />
        </el-icon>
        <span :style="levelStyle" class="el-tag log-level">{{ log.level }}</span>
        <div class="log-content d-inline-block">
            <span v-if="title" class="fw-bold">{{ log.taskId ?? log.flowId ?? "" }}</span>
            <div
                class="header"
                :class="{'d-inline-block': metaWithValue.length === 0, 'me-3': metaWithValue.length === 0}"
            >
                <span class="header-badge text-secondary">
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
            <div
                ref="lineContent"
                :class="{'d-inline': metaWithValue.length === 0, 'me-3': metaWithValue.length === 0}"
                v-html="renderedMarkdown"
            />
        </div>
    </div>
</template>
<script>
    import Convert from "ansi-to-html";
    import xss from "xss";
    import * as Markdown from "../../utils/markdown";
    import MenuRight from "vue-material-design-icons/MenuRight.vue";
    import linkify from "./linkify";


    let convert = new Convert();

    export default {
        components: {
            MenuRight,
        },
        props: {
            cursor: {
                type: Boolean,
                default: false,
            },
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
                default: false,
            },
        },
        data() {
            return {
                renderedMarkdown: undefined,
                logsFontSize: parseInt(localStorage.getItem("logsFontSize") || "12"),
            };
        },
        async created() {
            this.renderedMarkdown = await Markdown.render(this.message, {onlyLink: true});
        },
        computed: {
            logLineStyle() {
                return {
                    fontSize: `${this.logsFontSize}px`,
                };
            },
            metaWithValue() {
                const metaWithValue = [];
                const excludes = [
                    "message",
                    "timestamp",
                    "thread",
                    "taskRunId",
                    "level",
                    "index",
                    "attemptNumber",
                ];
                excludes.push.apply(excludes, this.excludeMetas);
                for (const key in this.log) {
                    if (this.log[key] && !excludes.includes(key)) {
                        let meta = {key, value: this.log[key]};
                        if (key === "executionId") {
                            meta["router"] = {
                                name: "executions/update",
                                params: {
                                    namespace: this.log["namespace"],
                                    flowId: this.log["flowId"],
                                    id: this.log[key],
                                },
                            };
                        }

                        if (key === "namespace") {
                            meta["router"] = {name: "flows/list", query: {namespace: this.log[key]}};
                        }

                        if (key === "flowId") {
                            meta["router"] = {
                                name: "flows/update",
                                params: {namespace: this.log["namespace"], id: this.log[key]},
                            };
                        }

                        metaWithValue.push(meta);
                    }
                }
                return metaWithValue;
            },
            levelStyle() {
                const lowerCaseLevel = this.log?.level?.toLowerCase();
                return {
                    "border-color": `var(--ks-log-border-${lowerCaseLevel})`,
                    "color": `var(--ks-log-content-${lowerCaseLevel})`,
                    "background-color": `var(--ks-log-background-${lowerCaseLevel})`,
                };
            },
            filtered() {
                return (
                    this.filter === "" || (this.log.message && this.log.message.toLowerCase().includes(this.filter))
                );
            },
            iconColor() {
                const logLevel = this.log.level?.toLowerCase();
                return `var(--ks-log-content-${logLevel}) !important`; // Use CSS variable for icon color
            },
            message() {
                let logMessage = !this.log.message
                    ? ""
                    : convert.toHtml(
                        xss(this.log.message, {
                            allowList: {span: ["style"]},
                        })
                    );

                logMessage = logMessage.replaceAll(
                    /(['"]?)(https?:\/\/[^'"\s]+)(['"]?)/g,
                    "$1<a href='$2' target='_blank'>$2</a>$3"
                );
                return logMessage;
            },
        },
        mounted() {
            window.addEventListener("storage", (event) => {
                if (event.key === "logsFontSize") {
                    this.logsFontSize = parseInt(event.newValue);
                }
            });

            setTimeout(() => {
                linkify(this.$refs.lineContent, this.$router);
            }, 200);
        },
        watch: {
            renderedMarkdown() {
                this.$nextTick(() => {
                    linkify(this.$refs.lineContent, this.$router);
                });
            },
        },
    };
</script>
<style scoped lang="scss">
div.line {
    cursor: text;
    white-space: pre-wrap;
    word-break: break-all;
    display: flex;
    align-items: flex-start;
    gap: 1rem;

    border-left-width: 2px !important;
    border-left-style: solid;
    border-left-color: transparent;

    border-top: 1px solid var(--ks-border-primary);

    // hack for class containing 0
    &[class*="-0"] {
        border-top: 0;
    }

    .icon_container {
        margin-left: -0.90rem;
    }

    .log-level {
        padding: .25rem;
        margin-top: 0.25rem;
    }

    .log-content {
        // prevent Firefox word breaks 
        flex-grow: 1;

        .header > * + * {
            margin-left: 1rem;
        }
    }

    .el-tag {
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
            border-radius: 4px;
        }
    }

    .message {
        line-height: 1.8;
    }

    p, :deep(.log-content p) {
        display: inline;
        margin-bottom: 0;
    }

    .log-level {
        padding: 0.25rem;
        border: 1px solid var(--ks-border-primary);
        user-select: none;
    }
}
</style>
