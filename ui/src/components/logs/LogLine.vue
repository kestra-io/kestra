<template>
    <div class="line text-monospace" v-if="filtered">
        <span :class="levelClass" class="header-badge log-level">{{ log.level.padEnd(9) }}</span>
        <span class="header-badge bg-light text-dark">
            {{ log.timestamp | date("iso") }}
        </span>
        <span v-for="(meta, x) in metaWithValue" :key="x">
            <span class="header-badge bg-light text-dark property">
                <span>{{ meta.key }}</span>
                <template v-if="meta.router">
                    <router-link :to="meta.router">{{ meta.value }}</router-link>
                </template>
                <template v-else>
                    {{ meta.value }}
                </template>

            </span>
        </span>
        <span class="message" v-html="message" />
    </div>
</template>
<script>
    import Convert from "ansi-to-html"
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
            },
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
                    TRACE: "badge-info",
                    DEBUG: "badge-secondary",
                    INFO: "badge-primary",
                    WARN: "badge-warning",
                    ERROR: "badge-danger",
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
                return !this.log.message ? "" : convert.toHtml(this.log.message);
            }
        },
    };
</script>
<style scoped lang="scss">
@use "sass:math";
@import "../../styles/_variable.scss";

div.line {
    white-space: pre-wrap;
    word-break: break-all;
    padding: 0 math.div($spacer, 2);

    .theme-dark & {
        color: var(--body-color)
    }

    .header-badge {
        display: inline-block;
        font-size: 95%;
        margin-left: math.div(-$spacer, 2);
        padding: $badge-padding-y $badge-padding-x;
        font-weight: $font-weight-base;
        line-height: 1;
        text-align: center;
        white-space: nowrap;
        vertical-align: baseline;
        margin-right: 10px;

        & a {
            margin-left: 6px;
            color: var(--dark);
        }

        &.log-level {
            white-space: pre;
        }

        &.property {
            padding: $badge-padding-y math.div($badge-padding-x, 2);

            > span {
                font-family: $font-family-sans-serif;
                color: var(--gray-600);
                user-select: none;
            }
        }

        .theme-dark &.bg-light, .theme-dark &.bg-light a {
            background-color: var(--gray-200-lighten-5) !important;
            color: var(--gray-600) !important;

            > span {
                color: var(--gray-300-lighten-10) !important;
            }
        }
    }

    .message {
        padding: 0 $badge-padding-x;
    }
}
</style>
