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
                <TaskIcon :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
            </div>

            <div class="card-body">
                <div class="card-title-row">
                    <span class="trigger-name">{{ displayName }}</span>
                    <span v-if="trigger.ee" class="ee-badge" :title="$t('triggers_add_ee_tooltip')">
                        EE
                    </span>
                </div>
                <div v-if="trigger.description" class="trigger-description">
                    <template v-for="(part, i) in descriptionParts" :key="i">
                        <code v-if="i % 2 === 1">{{ part.slice(1, -1) }}</code>
                        <template v-else>
                            {{ part }}
                        </template>
                    </template>
                </div>
            </div>

            <el-button type="primary" @click="$emit('add', trigger)">
                {{ $t("triggers_add_card_add") }}
            </el-button>
        </div>
    </el-tooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {computedAsync} from "@vueuse/core";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import {isMcpTrigger, triggerDisplayName} from "./triggerCatalog";
    import * as Markdown from "../../utils/markdown";

    const props = defineProps<{ trigger: TriggerPluginDto }>();
    defineEmits<{ (e: "add", trigger: TriggerPluginDto): void }>();

    const pluginsStore = usePluginsStore();
    const isMcp = computed(() => isMcpTrigger(props.trigger));
    const displayName = computed(() => triggerDisplayName(props.trigger));

    const descriptionParts = computed(() => (props.trigger.description ?? "").split(/(`[^`]+`)/g));

    const fullDescriptionHtml = computedAsync(async () => props.trigger.description
        ? await Markdown.render(props.trigger.description)
        : "");
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        align-items: center;
        gap: .75rem;
        padding: 1rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: .5rem;
        background: var(--ks-background-card);
        transition: all .12s ease;
        cursor: pointer;

        &:hover {
            border-color: var(--el-color-primary);
            box-shadow: 0 1px 2px #0001;
        }
    }

    .icon-chip {
        width: 2.25rem;
        height: 2.25rem;
        border-radius: .375rem;
        display: flex;
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
        gap: .375rem;
        min-width: 0;
    }

    .trigger-name,
    .trigger-description {
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .trigger-name {
        font-size: .875rem;
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .ee-badge {
        font-size: .625rem;
        font-weight: 600;
        letter-spacing: .05em;
        padding: 1px .375rem;
        border-radius: 3px;
        color: #a78bfa;
        background: color-mix(in srgb, #a78bfa 18%, transparent);
    }

    .trigger-description {
        margin-top: 2px;
        font-size: .75rem;
        line-height: 1.4;
        color: var(--ks-content-tertiary);

        :deep(code) {
            font-family: var(--font-monospace, monospace);
            font-size: .92em;
            padding: 1px .25rem;
            border-radius: 3px;
            background: var(--ks-background-code, #7c3aed14);
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
