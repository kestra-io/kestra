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
        <div class="trigger-card" @click="$emit('add', trigger)">
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
                    <span
                        v-if="trigger.ee"
                        class="ee-badge"
                        :title="$t('triggers.add.ee_tooltip')"
                    >EE</span>
                </div>
                <div
                    v-if="trigger.description"
                    class="trigger-description"
                    v-html="descriptionHtml"
                />
            </div>

            <el-button
                type="primary"
                @click="$emit('add', trigger)"
            >
                {{ $t("triggers.add.card.add") }}
            </el-button>
        </div>
    </el-tooltip>
</template>

<script setup lang="ts">
    import {computed, ref, watchEffect} from "vue";
    import {TaskIcon} from "@kestra-io/ui-libs";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import {isMcpTrigger, triggerDisplayName} from "./triggerCatalog";
    import * as Markdown from "../../utils/markdown";

    const props = defineProps<{
        trigger: TriggerPluginDto
    }>();

    defineEmits<{(e: "add", trigger: TriggerPluginDto): void}>();

    const pluginsStore = usePluginsStore();

    const isMcp = computed(() => isMcpTrigger(props.trigger));
    const displayName = computed(() => triggerDisplayName(props.trigger));

    const HTML_ENTITIES: Record<string, string> = {
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        "\"": "&quot;",
        "'": "&#39;",
    };

    const descriptionHtml = computed(() => {
        const raw = props.trigger.description ?? "";
        return raw
            .replace(/[&<>"']/g, c => HTML_ENTITIES[c])
            .replace(/`([^`]+)`/g, "<code>$1</code>");
    });

    const fullDescriptionHtml = ref<string>("");
    watchEffect(async () => {
        const description = props.trigger.description;
        fullDescriptionHtml.value = description
            ? await Markdown.render(description, {})
            : "";
    });
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        align-items: center;
        gap: 0.75rem;
        padding: 1rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.5rem;
        background: var(--ks-background-card);
        transition: border-color 0.12s ease, box-shadow 0.12s ease;

        &:hover {
            border-color: var(--el-color-primary);
            box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
        }
    }

    .icon-chip {
        width: 2.25rem;
        height: 2.25rem;
        border-radius: 0.375rem;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: color-mix(in srgb, var(--el-color-primary) 10%, transparent);
        color: var(--el-color-primary);

        :deep(img),
        :deep(svg) {
            width: 1.25rem;
            height: 1.25rem;
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
        gap: 0.375rem;
        min-width: 0;
    }

    .trigger-name {
        font-size: 0.875rem;
        font-weight: 600;
        color: var(--ks-content-primary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        min-width: 0;
    }

    .ee-badge {
        flex-shrink: 0;
        font-size: 0.625rem;
        font-weight: 600;
        letter-spacing: 0.05em;
        padding: 1px 0.375rem;
        border-radius: 3px;
        color: #a78bfa;
        background: color-mix(in srgb, #a78bfa 18%, transparent);
    }

    .trigger-description {
        margin-top: 2px;
        font-size: 0.75rem;
        line-height: 1.4;
        color: var(--ks-content-tertiary);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;

        :deep(code) {
            font-family: var(--font-monospace, monospace);
            font-size: 0.92em;
            padding: 1px 0.25rem;
            border-radius: 3px;
            background: var(--ks-background-code, rgba(124, 58, 237, 0.08));
            color: var(--ks-content-primary);
        }
    }
</style>

<style lang="scss">
    .trigger-card-tooltip {
        max-width: 26.25rem;
        font-size: 0.75rem;
        line-height: 1.5;
        padding: 0.625rem 0.75rem;
        color: var(--ks-content-primary);
    }
</style>
