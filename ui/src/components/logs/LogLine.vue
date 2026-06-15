<template>
    <div
        v-if="filtered"
        class="line"
        :class="[`log-row-${levelLower}`, `density-${logsDensity}`, {selected: cursor}]"
        :style="rowStyle"
    >
        <KsIcon v-if="cursor" class="icon_container" :style="{color: iconColor}" size="xl">
            <MenuRight />
        </KsIcon>
        <span
            class="log-level"
            :class="{'log-level--clickable': clickableLevel}"
            :style="{color: `var(--ks-log-${levelLower})`}"
            :role="clickableLevel ? 'button' : undefined"
            :tabindex="clickableLevel ? 0 : undefined"
            :title="clickableLevel ? t('filter_for') : undefined"
            @click="clickableLevel && props.log.level && emit('filter-level', props.log.level)"
            @keydown.enter="clickableLevel && props.log.level && emit('filter-level', props.log.level)"
            @keydown.space.prevent="clickableLevel && props.log.level && emit('filter-level', props.log.level)"
        >{{ levelLabel }}</span>
        <div class="log-content">
            <div class="log-header">
                <time class="log-time" :title="Filters.date(log.timestamp, 'iso')">{{ Filters.date(log.timestamp, "HH:mm:ss.SSS") }}</time>
                <span v-if="title" class="log-source">{{ log.taskId ?? log.flowId ?? "" }}</span>
                <span v-for="(meta, x) in metaWithValue" :key="x" class="log-meta">
                    <span class="log-meta-key">{{ meta.key }}</span>
                    <LogValueActions
                        :field="meta.key"
                        :value="String(meta.value)"
                        :filterable="isFilterable(meta.key)"
                        :to="meta.router"
                        @filter="emit('filter', $event)"
                    >{{ meta.value }}</LogValueActions>
                </span>
            </div>
            <KsJsonTree
                v-if="structured !== undefined"
                class="log-json"
                :value="structured"
                :defaultExpanded="logsExpandByDefault"
            />
            <pre v-else ref="lineContent" class="log-message" :style="messageStyle" v-html="renderedHtml" />
        </div>
        <CopyToClipboard class="log-copy" :text="`${log.level} ${log.timestamp} ${log.message}`" link />
    </div>
</template>
<script setup lang="ts">
    import {computed, nextTick, ref, watch, type CSSProperties} from "vue"
    import Convert from "ansi-to-html"
    import xss from "xss"
    import MenuRight from "vue-material-design-icons/MenuRight.vue"
    import linkify, {processLinkTags} from "./linkify"
    import CopyToClipboard from "../layout/CopyToClipboard.vue"
    import LogValueActions from "./LogValueActions.vue"
    import {isFilterableLogField} from "./logValueFilter"
    import {LevelKey, parseStructured} from "../../utils/logs"
    import {logsFontSize, logsDensity, logsBodyClamp, logsPrettyJson, logsExpandByDefault, DENSITY_PADDING} from "../../composables/useLogDisplay"
    import {Log} from "../../stores/logs"
    import {useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import * as Filters from "../../utils/filters"

    const {t} = useI18n()

    // Props
    const props = defineProps<{
        cursor?: boolean,
        log: Log,
        filter?: string,
        level?: LevelKey,
        excludeMetas?: (keyof Log)[],
        title?: boolean,
        raw?: boolean,
        clickableLevel?: boolean,
        highlight?: string
    }>()

    const emit = defineEmits<{
        filter: [{field: string, value: string, negate: boolean}],
        "filter-level": [level: string]
    }>()

    const isFilterable = isFilterableLogField

    // State
    const convert = new Convert()
    const lineContent = ref<HTMLElement>()
    const router = useRouter()

    // Computed
    const levelLower = computed(() => (props.log?.level ?? "info").toLowerCase())

    const rowStyle = computed(() => {
        const tinted = levelLower.value === "error" || levelLower.value === "warn"
        const pad = DENSITY_PADDING[logsDensity.value]
        return {
            fontSize: `${logsFontSize.value}px`,
            paddingTop: pad,
            paddingBottom: pad,
            borderLeftColor: `var(--ks-log-border-${levelLower.value})`,
            ...(tinted ? {background: `color-mix(in srgb, var(--ks-log-${levelLower.value}) 6%, transparent)`} : {}),
        }
    })

    const metaWithValue = computed(() => {
        const result: any[] = []
        const excludes:(keyof Log)[] = [
            "message",
            "timestamp",
            "thread",
            "taskRunId",
            "level",
            "index",
            "attemptNumber",
            "executionKind",
            ...(props.excludeMetas ?? []),
        ]
        for (const keyString in props.log) {
            const key = keyString as keyof Log
            if (props.log[key] && !excludes.includes(key)) {
                let meta: any = {key, value: props.log[key]}
                if (key === "executionId") {
                    meta["router"] = {
                        name: "executions/update",
                        params: {
                            namespace: props.log["namespace"],
                            flowId: props.log["flowId"],
                            id: props.log[key],
                        },
                    }
                }
                if (key === "namespace") {
                    meta["router"] = {name: "flows/list", query: {namespace: props.log[key]}}
                }
                if (key === "flowId") {
                    meta["router"] = {
                        name: "flows/update",
                        params: {namespace: props.log["namespace"], id: props.log[key]},
                    }
                }
                result.push(meta)
            }
        }
        return result
    })

    const levelLabel = computed(() => {
        const level = props.log?.level ?? ""
        return level.charAt(0).toUpperCase() + level.slice(1).toLowerCase()
    })


    const filtered = computed(() =>
        props.filter === "" || (props.log.message && props.log.message.toLowerCase().includes(props.filter ?? "")),
    )

    const iconColor = computed(() => {
        const logLevel = props.log.level?.toLowerCase()
        return `var(--ks-log-${logLevel}) !important`
    })

    const renderedHtml = computed(() => {
        const raw = props.log.message ?? ""
        let html = convert.toHtml(
            xss(raw, {
                allowList: {span: ["style"]},
            }),
        )

        html = processLinkTags(html)
        html = html.replaceAll(
            /(['"]?)(https?:\/\/[^'"\s]+)(['"]?)/g,
            "$1<a href='$2' target='_blank'>$2</a>$3",
        )

        const term = (props.highlight ?? props.filter)?.trim()
        if (term) {
            const matcher = new RegExp(term.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"), "gi")
            html = html.replace(/<[^>]*>|&[^;\s]+;|[^<&]+/g, (token) =>
                token[0] === "<" || token[0] === "&"
                    ? token
                    : token.replace(matcher, "<mark class=\"log-highlight\">$&</mark>"),
            )
        }

        return html
    })

    const structured = computed(() => (props.raw || !logsPrettyJson.value ? undefined : parseStructured(props.log.message)))

    const messageStyle = computed<CSSProperties>(() => (logsBodyClamp.value > 0 && logsDensity.value !== "compact"
        ? {display: "-webkit-box", "-webkit-line-clamp": String(logsBodyClamp.value), "-webkit-box-orient": "vertical", overflow: "hidden"}
        : {}))

    watch(renderedHtml, () => {
        nextTick(() => {
            linkify(lineContent.value, router)
        })
    }, {immediate: true})
</script>
<style scoped lang="scss">
div.line {
    position: relative;
    display: flex;
    align-items: flex-start;
    gap: var(--ks-spacing-3);
    padding: var(--ks-spacing-2) var(--ks-spacing-3);
    min-height: 1.9rem;
    border-left: 2px solid transparent;
    border-top: 1px solid var(--ks-border-subtle);
    transition: background 0.1s ease-in-out;

    &:hover {
        background: var(--ks-bg-hover);
    }

    &.selected {
        background: var(--ks-bg-tag-hover);
    }

    &[class*="-0"] {
        border-top: 0;
    }

    .icon_container {
        position: absolute;
        left: -0.6rem;
        top: 0.5rem;
        z-index: 1;
    }

    .log-level {
        flex: none;
        width: 3.5rem;
        padding-top: 1px;
        font-family: var(--ks-font-family-sans);
        font-weight: 700;
        letter-spacing: 0.04em;
        text-transform: uppercase;
        line-height: 1.6;
        user-select: none;

        &--clickable {
            cursor: pointer;
            border-radius: var(--ks-radius-xs);

            &:hover {
                background: var(--ks-bg-hover);
            }
        }
    }

    .log-time {
        flex: none;
        font-family: var(--ks-font-family-mono);
        color: var(--ks-text-dim);
        white-space: nowrap;
        user-select: none;
        font-variant-numeric: tabular-nums;
    }

    .log-content {
        flex: 1 1 auto;
        min-width: 0;
        line-height: 1.6;
    }

    &.density-compact {
        .log-content {
            line-height: 1.4;
        }

        .log-message {
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }
    }

    &.density-expanded .log-content {
        line-height: 1.95;
    }

    .log-header {
        display: flex;
        flex-wrap: wrap;
        align-items: baseline;
        gap: var(--ks-spacing-1) var(--ks-spacing-2);
        min-height: 1.4rem;
    }

    .log-source {
        font-family: var(--ks-font-family-sans);
        font-weight: 600;
        color: var(--ks-text-secondary);
    }

    .log-meta {
        display: inline-flex;
        align-items: baseline;
        gap: 0.1875rem;
        padding: 0.0625rem var(--ks-spacing-1);
        border-radius: var(--ks-radius-xs);
        background: var(--ks-bg-tag);
        font-family: var(--ks-font-family-sans);

        .log-meta-key {
            color: var(--ks-text-dim);

            &::after {
                content: ":";
            }
        }

    }

    .log-message {
        display: block;
        margin: 0.125rem 0 0;
        padding: 0;
        background: transparent;
        font-family: var(--ks-font-family-mono);
        font-size: inherit;
        color: var(--ks-text-primary);
        white-space: pre-wrap;
        overflow-wrap: anywhere;
        word-break: break-word;
    }

    :deep(.log-message p) {
        display: inline;
        margin-bottom: 0;
    }

    :deep(.log-highlight) {
        background: var(--ks-bg-tag-active);
        color: var(--ks-text-primary);
        border-radius: var(--ks-radius-xs);
        padding: 0 2px;
    }

    .log-json {
        display: block;
        margin-top: 2px;
    }

    :deep(.clipboard) {
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.15s ease-in-out;
        top: 0.4rem;
        right: 0.5rem;
    }

    &:hover :deep(.clipboard) {
        opacity: 1;
        pointer-events: auto;
    }
}
</style>
