<template>
    <div class="line font-monospace" v-if="filtered">
        <span :class="levelClass" class="header-badge log-level el-tag noselect">{{ log.level }}</span>
        <div class="log-content d-inline-block">
            <div class="header" :class="{'d-inline-block': metaWithValue.length === 0, 'me-3': metaWithValue.length === 0}">
                <span class="header-badge noselect">
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
            <span class="message" v-html="message" />
        </div>
    </div>
</template>
<script>
    import Convert from "ansi-to-html"
    import xss from "xss";
    let convert = new Convert();

    export default {
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
            }
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
                            meta["router"] = {name: "executions/update", params: {
                                namespace: this.log["namespace"],
                                flowId: this.log["flowId"],
                                id: this.log[key]
                            }};
                        }

                        if (key === "namespace") {
                            meta["router"] = {name: "flows/list", query: {namespace: this.log[key]}};
                        }


                        if (key === "flowId") {
                            meta["router"] = {name: "flows/update", params: {namespace: this.log["namespace"], id: this.log[key]}};
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
                return !this.log.message ? "" : convert.toHtml(xss(this.log.message));
            }
        },
    };
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    div.line {
        white-space: pre-wrap;
        word-break: break-all;
        padding: calc(var(--spacer) / 2);
        display: flex;
        align-items: start;
        gap: $spacer;
        line-height: 1.66;

        .log-level {
            padding: calc(var(--spacer) / 4);
        }

        .log-content {
            .header {
                margin-bottom: calc(var(--spacer) / 4);

                > * + *{
                    margin-left: $spacer;
                }
            }
        }

        span {
            margin-bottom: 2px;
        }
        .el-tag {
            border-radius: 0;
            border: 0;
            height: auto;
        }

        .header-badge {
            display: inline-block;
            font-size: 95%;
            text-align: center;
            white-space: nowrap;
            vertical-align: baseline;

            span:first-child {
                margin-right: 6px;
                font-family: var(--bs-font-sans-serif);
                color: #574F6C;

                html.dark & {
                    color: var(--bs-gray-900);
                }

                user-select: none;

                &::after{
                    content: ":";
                }
            }
            &:not(.el-tag):not(.noselect), & a {
                color: $indigo;
                border-radius: var(--bs-border-radius);
            }

            & a:hover {
                color: var(--bs-link-color);
            }

            &.log-level {
                white-space: pre;
                border-radius: var(--bs-border-radius);
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
            color: #574F6C;

            html.dark & {
                color: #C6C1D9;
            }
        }
    }
</style>
