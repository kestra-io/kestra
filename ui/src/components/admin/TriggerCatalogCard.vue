<template>
    <el-tooltip
        :disabled="!trigger.description"
        placement="top"
        :showAfter="250"
        :hideAfter="0"
        effect="light"
        popperClass="trigger-card-tooltip"
        :rawContent="true"
        :content="fullDescriptionHtml"
    >
        <div
            class="trigger-card"
            role="button"
            tabindex="0"
            @click="$emit('add', trigger)"
            @keydown.enter.space.prevent="$emit('add', trigger)"
        >
            <div class="icon-chip" :class="{'icon-chip--mcp': isMcp}">
                <TaskIcon
                    :cls="trigger.type"
                    :icons="pluginsStore.icons"
                    onlyIcon
                />
            </div>

            <div class="card-body">
                <div class="card-title-row">
                    <span class="trigger-name">{{ displayName }}</span>
                    <span v-if="trigger.ee" class="ee-badge" :title="$t('triggers.add.ee_tooltip')">EE</span>
                </div>
                <div
                    v-if="trigger.description"
                    class="trigger-description"
                    v-html="descriptionHtml"
                />
            </div>

            <button type="button" class="add-button" @click.stop="$emit('add', trigger)">
                {{ $t("triggers.add.card.add") }}
            </button>
        </div>
    </el-tooltip>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue";
    import {TaskIcon} from "@kestra-io/ui-libs";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import * as Markdown from "../../utils/markdown";

    const props = defineProps<{
        trigger: TriggerPluginDto;
    }>();

    defineEmits<{
        (e: "add", trigger: TriggerPluginDto): void;
    }>();

    const pluginsStore = usePluginsStore();

    const isMcp = computed(() => props.trigger.type.endsWith(".McpTool"));

    // Prefer the simple class name. Plugins that name their trigger just `Trigger`
    // (e.g. io.kestra.plugin.gcp.gcs.Trigger) fall back to the last package segment
    // so users see "GCS", "BigQuery", etc. instead of a wall of identical "Trigger"s.
    const displayName = computed(() => {
        const parts = props.trigger.type.split(".");
        const simple = parts[parts.length - 1];
        if (simple && simple !== "Trigger") {
            return simple;
        }
        const subgroup = parts[parts.length - 2] ?? simple;
        return humanize(subgroup);
    });

    // Truncated inline preview: escape HTML, then turn `code` into <code>.
    // Descriptions from plugin @Schema annotations often embed identifiers like
    // `interval` or `uri` in backticks, and the catalog looks much cleaner with
    // those formatted as inline code than rendered literally.
    const descriptionHtml = computed(() => {
        const raw = props.trigger.description ?? "";
        const escaped = raw
            .replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
        return escaped.replace(/`([^`]+)`/g, "<code>$1</code>");
    });

    // Full rendered markdown for the hover tooltip. The card's inline preview
    // is truncated by CSS; the tooltip shows the complete description so users
    // can read long or multi-paragraph schema docs without leaving the page.
    const fullDescriptionHtml = ref<string>("");
    watch(
        () => props.trigger.description,
        async (description) => {
            if (!description) {
                fullDescriptionHtml.value = "";
                return;
            }
            fullDescriptionHtml.value = await Markdown.render(description, {});
        },
        {immediate: true},
    );

    // Known subgroup → display-name overrides for CamelCase acronyms that simple
    // casing can't guess right (BigQuery vs Bigquery, etc.).
    const SUBGROUP_OVERRIDES: Record<string, string> = {
        bigquery: "BigQuery",
        mongodb: "MongoDB",
        dynamodb: "DynamoDB",
        eventbridge: "EventBridge",
        servicebus: "ServiceBus",
        postgresql: "PostgreSQL",
        mysql: "MySQL",
        mssql: "MSSQL",
        graphql: "GraphQL",
        pubsub: "PubSub",
        opensearch: "OpenSearch",
        elasticsearch: "Elasticsearch",
        githubactions: "GitHub Actions",
    };

    function humanize(segment: string): string {
        if (!segment) return segment;
        const lower = segment.toLowerCase();
        if (SUBGROUP_OVERRIDES[lower]) return SUBGROUP_OVERRIDES[lower];
        // Short acronyms (gcs, sqs, http, sftp, ftp, aws, gcp...) → uppercase.
        if (segment.length <= 4 && segment === lower) return segment.toUpperCase();
        // Already has caps (Kafka, Airflow) → leave as-is.
        if (segment !== lower) return segment;
        // Default: capitalize.
        return segment.charAt(0).toUpperCase() + segment.slice(1);
    }
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px;
        border: 1px solid var(--ks-border-primary, var(--bs-border-color));
        border-radius: 8px;
        background: var(--ks-background-card, var(--bs-body-bg));
        cursor: pointer;
        transition: border-color 0.12s ease, box-shadow 0.12s ease;

        &:hover,
        &:focus-visible {
            border-color: var(--el-color-primary);
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
            outline: none;
        }
    }

    .icon-chip {
        width: 36px;
        height: 36px;
        border-radius: 6px;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
        color: var(--el-color-primary);

        :deep(img), :deep(svg) {
            width: 20px;
            height: 20px;
        }

        &--mcp {
            background: color-mix(in srgb, #ec4899 12%, transparent);
            color: #ec4899;
        }
    }

    .card-body {
        flex: 1;
        min-width: 0;
    }

    .card-title-row {
        display: flex;
        align-items: center;
        gap: 6px;
        min-width: 0;
    }

    .trigger-name {
        font-size: 14px;
        font-weight: 600;
        color: var(--ks-content-primary, var(--bs-body-color));
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        min-width: 0;
    }

    // Lighter purple, matching the modal header's EE badge.
    .ee-badge {
        flex-shrink: 0;
        font-size: 10px;
        font-weight: 600;
        letter-spacing: 0.05em;
        padding: 1px 6px;
        border-radius: 3px;
        color: #a78bfa;
        background: color-mix(in srgb, #a78bfa 18%, transparent);
    }

    .trigger-description {
        margin-top: 2px;
        font-size: 12px;
        line-height: 1.4;
        color: var(--ks-content-tertiary, var(--bs-gray-600));
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        :deep(code) {
            font-family: var(--font-monospace, monospace);
            font-size: 0.92em;
            padding: 1px 4px;
            border-radius: 3px;
            background: var(--ks-background-code, rgba(124, 58, 237, 0.08));
            color: var(--ks-content-primary, var(--bs-body-color));
        }
    }

    .add-button {
        flex-shrink: 0;
        padding: 6px 14px;
        background: var(--el-color-primary);
        border: 1px solid var(--el-color-primary);
        border-radius: 6px;
        color: #fff;
        font-size: 13px;
        font-weight: 500;
        cursor: pointer;
        transition: background-color 0.12s ease, border-color 0.12s ease;

        &:hover {
            background: var(--el-color-primary-dark-2, #6d28d9);
            border-color: var(--el-color-primary-dark-2, #6d28d9);
        }
    }
</style>
