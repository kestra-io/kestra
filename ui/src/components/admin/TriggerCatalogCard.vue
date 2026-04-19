<template>
    <div class="trigger-card">
        <div class="icon-wrapper">
            <TaskIcon
                :cls="trigger.type"
                :icons="pluginsStore.icons"
                onlyIcon
            />
        </div>

        <div class="trigger-info">
            <div class="trigger-title-row">
                <span class="trigger-name">{{ trigger.name }}</span>
                <span v-if="trigger.ee" class="ee-badge" :title="$t('triggers.add.ee_tooltip')">EE</span>
            </div>
            <p v-if="trigger.description" class="trigger-description" :title="trigger.description">
                {{ trigger.description }}
            </p>
        </div>

        <el-button type="primary" class="add-button" @click="$emit('add', trigger)">
            {{ $t("triggers.add.card.add_to_flow") }}
        </el-button>
    </div>
</template>

<script setup lang="ts">
    import {TaskIcon} from "@kestra-io/ui-libs";

    import {usePluginsStore, type TriggerPluginDto} from "../../stores/plugins";

    defineProps<{
        trigger: TriggerPluginDto;
    }>();

    defineEmits<{
        (e: "add", trigger: TriggerPluginDto): void;
    }>();

    const pluginsStore = usePluginsStore();
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        align-items: center;
        gap: .75rem;
        padding: .75rem 1rem;
        border: 1px solid var(--bs-border-color);
        border-radius: 8px;
        background: var(--bs-body-bg);
        transition: border-color .15s ease, box-shadow .15s ease;

        &:hover {
            border-color: var(--el-color-primary);
            box-shadow: 0 2px 6px rgba(0, 0, 0, .04);
        }
    }

    .icon-wrapper {
        width: 2.25rem;
        height: 2.25rem;
        border-radius: 6px;
        display: flex;
        align-items: center;
        justify-content: center;
        flex-shrink: 0;
        background: var(--bs-gray-100);

        :deep(img), :deep(svg) {
            width: 1.5rem;
            height: 1.5rem;
        }
    }

    .trigger-info {
        flex: 1;
        min-width: 0;
    }

    .trigger-title-row {
        display: flex;
        align-items: center;
        gap: .5rem;
    }

    .trigger-name {
        font-weight: 600;
        color: var(--bs-body-color);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .ee-badge {
        font-size: 9px;
        font-weight: 700;
        letter-spacing: .15em;
        padding: 1px 4px;
        border-radius: 3px;
        color: #b45309;
        background: #fef3c7;
        border: 1px solid #fcd34d;
    }

    .trigger-description {
        margin: 0;
        font-size: .85rem;
        color: var(--bs-gray-600);
        white-space: nowrap;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .add-button {
        flex-shrink: 0;
    }
</style>
