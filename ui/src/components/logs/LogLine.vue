<template>
    <div
        class="py-2 line font-monospace"
        :class="{['log-border-' + log.level.toLowerCase()]: cursor && log.level !== undefined}"
        v-if="filtered"
        :style="logLineStyle"
    >
        <el-icon v-if="cursor" class="icon_container" :style="{color: iconColor}" :size="28">
            <MenuRight />
        </el-icon>
        <div class="log-content d-inline-block">
            <span v-if="title" class="fw-bold">{{ log.taskId ?? log.flowId ?? "" }}</span>
            <div
                class="header"
                :class="{'d-inline-block': metaWithValue.length === 0, 'me-3': metaWithValue.length === 0}"
            >
                <span :style="levelStyle" class="el-tag log-level">{{ log.level }}</span>
                <span class="header-badge text-secondary">
                    {{ Filters.date(log.timestamp, "iso") }}
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
        <CopyToClipboard :text="`${log.level} ${log.timestamp} ${log.message}`" link />
    </div>
</template>
<script setup lang="ts">
    import {ref, computed, onMounted, watch, nextTick} from "vue";
    import Convert from "ansi-to-html";
    import {useStorage} from "@vueuse/core";
    import xss from "xss";
    import * as Markdown from "../../utils/markdown";
    import MenuRight from "vue-material-design-icons/MenuRight.vue";
    import linkify from "./linkify";
    import CopyToClipboard from "../layout/CopyToClipboard.vue";
    import {LevelKey} from "../../utils/logs";
    import {Log} from "../../stores/logs";
    import {useRouter} from "vue-router";
    import * as Filters from "../../utils/filters";

    // Props
    const props = defineProps<{
        cursor?: boolean,
        log: Log,
        filter?: string,
        level?: LevelKey,
        excludeMetas?: (keyof Log)[],
        title?: boolean
    }>();

    // State
    const renderedMarkdown = ref<string | undefined>(undefined);
    const logsFontSize = useStorage<number>("logsFontSize", 12);
    const lineContent = ref<HTMLElement>();

    const convert = new Convert();

    // Computed
    const logLineStyle = computed(() => ({
        fontSize: `${logsFontSize.value}px`,
    }));

    const metaWithValue = computed(() => {
        const metaWithValue: any[] = [];
        const excludes:(keyof Log)[] = [
            "message",
            "timestamp",
            "thread",
            "taskRunId",
            "level",
            "index",
            "attemptNumber",
            "executionKind",
            ...(props.excludeMetas ?? [])
        ];
        for (const keyString in props.log) {
            const key = keyString as keyof Log;
            if (props.log[key] && !excludes.includes(key)) {
                let meta: any = {key, value: props.log[key]};
                if (key === "executionId") {
                    meta["router"] = {
                        name: "executions/update",
                        params: {
                            namespace: props.log["namespace"],
                            flowId: props.log["flowId"],
                            id: props.log[key],
                        },
                    };
                }
                if (key === "namespace") {
                    meta["router"] = {name: "flows/list", query: {namespace: props.log[key]}};
                }
                if (key === "flowId") {
                    meta["router"] = {
                        name: "flows/update",
                        params: {namespace: props.log["namespace"], id: props.log[key]},
                    };
                }
                metaWithValue.push(meta);
            }
        }
        return metaWithValue;
    });

    const levelStyle = computed(() => {
        const lowerCaseLevel = props.log?.level?.toLowerCase();
        return {
            "border-color": `var(--ks-log-border-${lowerCaseLevel})`,
            "color": `var(--ks-log-content-${lowerCaseLevel})`,
            "background-color": `var(--ks-log-background-${lowerCaseLevel})`,
        };
    });

    const filtered = computed(() =>
        props.filter === "" || (props.log.message && props.log.message.toLowerCase().includes(props.filter ?? ""))
    );

    const iconColor = computed(() => {
        const logLevel = props.log.level?.toLowerCase();
        return `var(--ks-log-content-${logLevel}) !important`;
    });

    const message = computed(() => {
        let logMessage = !props.log.message
            ? ""
            : convert.toHtml(
                xss(props.log.message, {
                    allowList: {span: ["style"]},
                })
            );
        logMessage = logMessage.replaceAll(
            /(['"]?)(https?:\/\/[^'"\s]+)(['"]?)/g,
            "$1<a href='$2' target='_blank'>$2</a>$3"
        );
        return logMessage;
    });

    const router = useRouter()
    onMounted(() => {
        setTimeout(() => {
            linkify(lineContent.value, router);
        }, 200);
    });

    watch(renderedMarkdown, () => {
        nextTick(() => {
            linkify(lineContent.value, router);
        });
    });

    // Initial markdown render
    (async () => {
        renderedMarkdown.value = await Markdown.render(message.value, {onlyLink: true, html: true});
    })();
</script>
<style scoped lang="scss">
div.line {
    position: relative;
    cursor: text;
    white-space: pre-wrap;
    word-break: break-all;
    display: block;

    border-left-width: 2px !important;
    border-left-style: solid;
    border-left-color: transparent;

    border-top: 1px solid var(--ks-border-primary);

    // hack for class containing 0
    &[class*="-0"] {
        border-top: 0;
    }

    .icon_container {
        position: absolute;
        left: -0.60rem;
        top: 50%;
        transform: translateY(-50%);
        z-index: 1;
    }

    .log-level {
        padding: .25rem;
        margin-top: 0;
        display: inline-flex;
        vertical-align: middle;
    }

    .log-content {
        display: inline-block;
        vertical-align: middle;
        overflow-wrap: anywhere;
        word-break: break-word;
        min-width: 0;

        .header {
            display: inline-flex;
            align-items: center;
            gap: .5rem;
        }

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
        width: auto;
        min-width: 40px;

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

    :deep(.clipboard) {
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.15s ease-in-out;
    }

    &:hover :deep(.clipboard) {
        opacity: 1;
        pointer-events: auto;
    }
}
</style>
