<template>
    <KsCard class="blueprint-card" shadow="never" @click="emit('click')">
        <div class="content">
            <h3 class="title">
                {{ blueprint.title ?? blueprint.id }}
            </h3>

            <div v-if="tagList.length || blueprint.template" class="tags">
                <span v-for="tag in tagList" :key="tag.id" class="tag">
                    {{ tag.label }}
                </span>
                <span v-if="blueprint.template" class="tag">
                    {{ $t('template') }}
                </span>
            </div>

            <div class="footer">
                <div class="icons">
                    <span v-for="task in visibleTasks" :key="task" class="icon">
                        <KsTaskIcon :cls="task" :icons="icons" />
                    </span>
                    <span v-if="overflowCount" class="overflow">
                        +{{ overflowCount }}
                    </span>
                </div>

                <div class="actions">
                    <slot
                        name="buttons"
                        :blueprint="{...blueprint, kind: blueprintKind, type: blueprintType}"
                    >
                        <KsButton
                            v-if="(!embed || system) && userCanCreate"
                            size="default"
                            @click.prevent.stop="emit('use')"
                        >
                            {{ $t('use') }}
                        </KsButton>
                    </slot>
                </div>
            </div>
        </div>
    </KsCard>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import {canCreate} from "override/composables/blueprintsPermissions"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const MAX_ICONS = 5

    const props = withDefaults(defineProps<{
        blueprint: FlowBlueprint;
        blueprintKind: "flow" | "dashboard" | "app";
        blueprintType: "community" | "custom";
        system?: boolean;
        embed?: boolean;
        tags?: Record<string, BlueprintTag>;
        icons?: Record<string, any>;
    }>(), {
        system: false,
        embed: false,
        icons: () => ({}),
    })

    const emit = defineEmits<{
        click: [];
        use: [];
    }>()

    const tagList = computed(() =>
        props.system
            ? []
            : (props.blueprint.tags ?? []).map(id => ({
                id,
                label: props.tags?.[id]?.name ?? id,
            })),
    )

    const tasks = computed(() =>
        [...new Set(props.blueprint.includedTasks)],
    )
    
    const visibleTasks = computed(() =>
        tasks.value.slice(0, MAX_ICONS),
    )

    const overflowCount = computed(() =>
        Math.max(0, tasks.value.length - MAX_ICONS),
    )

    const userCanCreate = computed(() =>
        canCreate(props.blueprintKind),
    )
</script>

<style scoped lang="scss">
    .blueprint-card {
        display: flex;
        cursor: pointer;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
        box-shadow: 0 2px 8px 0 var(--ks-shadow-surface);
        transition: border-color 0.2s ease, box-shadow 0.2s ease;

        &:hover {
            border-color: var(--ks-border-strong);
            box-shadow: 0 0.5rem 1rem 0 var(--ks-shadow-element);
        }

        :deep(.kel-card__body) {
            width: 100%;
            height: 100%;
        }
    }

    .content {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        width: 100%;
        height: 100%;
    }

    .title {
        display: -webkit-box;
        margin: 0;
        overflow: hidden;
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-md);
        font-weight: 600;
        overflow-wrap: break-word;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        line-clamp: 2;
    }

    .tags {
        display: flex;
        flex-wrap: wrap;
        gap: var(--ks-spacing-2);

        .tag {
            display: inline-flex;
            align-items: center;
            height: 1.5rem;
            padding: 0.125rem 0.375rem;
            border-radius: var(--ks-radius-sm);
            background: var(--ks-bg-tag);
            color: var(--ks-text-primary);
            font-size: var(--ks-font-size-xs);
        }
    }

    .footer {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        margin-top: auto;
        padding-top: var(--ks-spacing-3);
        border-top: 1px solid var(--ks-border-subtle);

        .icons {
            display: flex;
            flex: 1 1 auto;
            align-items: center;
            gap: var(--ks-spacing-2);
            min-width: 0;
            overflow: hidden;

            .icon {
                display: flex;
                flex-shrink: 0;

                :deep(.ks-task-icon) {
                    width: 1.5rem;
                    height: 1.5rem;
                }
            }

            .overflow {
                display: flex;
                flex-shrink: 0;
                align-items: center;
                justify-content: center;
                height: 1.5rem;
                min-width: 1.5rem;
                padding: 0 0.375rem;
                border-radius: var(--ks-radius-xs);
                background: var(--ks-bg-tag);
                color: var(--ks-text-secondary);
                font-size: var(--ks-font-size-xs);
            }
        }

        .actions {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            flex-shrink: 0;
        }
    }
</style>
