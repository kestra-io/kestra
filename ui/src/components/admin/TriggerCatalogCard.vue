<template>
    <KsTooltip
        :disabled="!trigger.description"
        placement="top"
        :showAfter="250"
        :hideAfter="0"
        effect="light"
        popperClass="trigger-card-tooltip"
    >
        <template #content>
            <KsMarkdown v-if="trigger.description" :content="trigger.description" />
        </template>
        <div class="trigger-card" @click="$emit('add', trigger)">
            <div class="card-header">
                <KsTaskIcon class="trigger-icon" :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
                <span class="trigger-name">{{ displayName }}</span>
                <KsTag
                    v-if="trigger.ee"
                    type="info"
                    size="small"
                    :title="$t('triggers_add_ee_tooltip')"
                >
                    EE
                </KsTag>
                <KsButton type="primary" class="add-button" @click="$emit('add', trigger)">
                    {{ $t("triggers_add_card_add") }}
                </KsButton>
            </div>
            <div class="trigger-description">
                <template v-for="(part, i) in descriptionParts" :key="i">
                    <code v-if="i % 2 === 1">{{ part.slice(1, -1) }}</code>
                    <template v-else>
                        {{ part }}
                    </template>
                </template>
            </div>
        </div>
    </KsTooltip>
</template>

<script setup lang="ts">
    import {computed} from "vue";
    import {KsMarkdown, KsTaskIcon} from "@kestra-io/design-system";
    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";
    import {triggerDisplayName} from "./triggerCatalog";

    const props = defineProps<{ trigger: TriggerPluginDto }>();
    defineEmits<{ (e: "add", trigger: TriggerPluginDto): void }>();

    const pluginsStore = usePluginsStore();
    const displayName = computed(() => triggerDisplayName(props.trigger));
    const descriptionParts = computed(() => (props.trigger.description ?? "").split(/(`[^`]+`)/g));
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        flex-direction: column;
        gap: .5rem;
        padding: 1rem;
        border: 1px solid var(--ks-border-primary);
        border-radius: .5rem;
        background: var(--ks-background-card);
        transition: all .12s ease;
        cursor: pointer;

        &:hover {
            border-color: var(--ks-border-active);
        }
    }

    .card-header {
        display: flex;
        align-items: center;
        gap: .5rem;
        min-width: 0;
    }

    .trigger-icon {
        width: 2rem;
        height: 2rem;
        flex-shrink: 0;
    }

    .add-button {
        margin-left: auto;
    }

    .trigger-name {
        flex: 1;
        min-width: 0;
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
        font-size: .875rem;
        font-weight: 600;
        color: var(--ks-content-primary);
    }

    .trigger-description {
        font-size: .75rem;
        line-height: 1.4;
        color: var(--ks-content-tertiary);
        min-height: calc(1.4em * 2);
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;

        code {
            font-family: var(--font-monospace, monospace);
            font-size: .92em;
            padding: 1px .25rem;
            border-radius: 3px;
            background: color-mix(in srgb, var(--ks-content-link) 12%, transparent);
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
