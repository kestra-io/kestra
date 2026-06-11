<template>
    <div class="trigger-card">
        <div class="trigger-body">
            <div class="icon">
                <KsTaskIcon class="glyph" :cls="trigger.type" :icons="pluginsStore.icons" onlyIcon />
            </div>
            <div class="content">
                <div class="header">
                    <span class="name">{{ displayName }}</span>
                    <KsTooltip
                        v-if="trigger.description"
                        placement="bottom-end"
                        :showAfter="250"
                        :hideAfter="0"
                        effect="light"
                        :popperStyle="TOOLTIP_POPPER_STYLE"
                    >
                        <template #content>
                            <KsMarkdown :content="trigger.description" />
                        </template>
                        <KsIcon class="info" :size="16">
                            <InformationOutline />
                        </KsIcon>
                    </KsTooltip>
                </div>
                <div class="description">
                    <template v-for="(part, i) in descriptionParts" :key="i">
                        <code v-if="i % 2 === 1">{{ part.slice(1, -1) }}</code>
                        <template v-else>
                            {{ part }}
                        </template>
                    </template>
                </div>
            </div>
        </div>

        <div class="divider" />

        <div class="footer">
            <div class="tags">
                <span class="tag">{{ $t(`triggers_add_filter_${trigger.group}`) }}</span>
                <KsTag
                    v-if="trigger.ee"
                    type="warning"
                    size="small"
                    :title="$t('triggers_add_ee_tooltip')"
                >
                    EE
                </KsTag>
            </div>
            <KsButton class="add" @click="$emit('add', trigger)">
                <template #icon>
                    <Plus />
                </template>
                {{ $t("triggers_add_card_add") }}
            </KsButton>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsMarkdown, KsTaskIcon} from "@kestra-io/design-system"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import {usePluginsStore, type TriggerPluginDto} from "../../../stores/plugins"
    import {triggerDisplayName} from "./triggerCatalog"

    const props = defineProps<{ trigger: TriggerPluginDto }>()
    defineEmits<{ add: [trigger: TriggerPluginDto] }>()

    const TOOLTIP_POPPER_STYLE = {
        maxWidth: "26.25rem",
        fontSize: "var(--ks-font-size-xs)",
        lineHeight: "var(--ks-line-height-base)",
        padding: "0.625rem var(--ks-spacing-3)",
        color: "var(--ks-content-primary)",
    }

    const pluginsStore = usePluginsStore()
    const displayName = computed(() => triggerDisplayName(props.trigger))
    const descriptionParts = computed(() => (props.trigger.description ?? "").split(/(`[^`]+`)/g))
</script>

<style scoped lang="scss">
    .trigger-card {
        display: flex;
        flex-direction: column;
        min-height: 8.75rem;
        padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
        border: var(--ks-border-block-primary);
        border-radius: var(--ks-radius-base);
        background: var(--ks-bg-surface);
        box-shadow: 0 2px 8px 0 var(--ks-shadow-surface);
        transition: border-color var(--ks-duration-base) var(--ks-ease-standard);

        &:hover {
            border-color: var(--ks-border-strong);
        }

        .trigger-body {
            display: flex;
            gap: var(--ks-spacing-3);
            flex: 1;
            min-height: 0;

            .icon {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 2.625rem;
                height: 2.625rem;
                flex-shrink: 0;
                border-radius: var(--ks-radius-base);
                border: 0.53px solid var(--ks-border-default);
                background: var(--ks-white);
                box-shadow: 0 0.53px 2.13px 0 var(--ks-shadow-element);

                .glyph {
                    width: var(--ks-icon-size-xl);
                    height: var(--ks-icon-size-xl);
                }
            }

            .content {
                display: flex;
                flex-direction: column;
                gap: var(--ks-spacing-1);
                flex: 1;
                min-width: 0;

                .header {
                    display: flex;
                    align-items: center;
                    gap: var(--ks-spacing-2);
                    min-width: 0;

                    .name {
                        flex: 1;
                        min-width: 0;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        font-size: var(--ks-font-size-md);
                        font-weight: var(--ks-font-weight-semibold);
                        color: var(--ks-text-primary);
                    }

                    .info {
                        flex-shrink: 0;
                        color: var(--ks-icon-muted);

                        &:hover {
                            color: var(--ks-text-primary);
                            cursor: pointer;
                        }
                    }
                }

                .description {
                    font-size: var(--ks-font-size-xs);
                    line-height: 20px;
                    color: var(--ks-text-secondary);
                    min-height: calc(1.4em * 2);
                    display: -webkit-box;
                    line-clamp: 2;
                    -webkit-line-clamp: 2;
                    -webkit-box-orient: vertical;
                    overflow: hidden;

                    code {
                        font-family: var(--ks-font-family-mono);
                        font-size: 0.92em;
                        padding: var(--ks-spacing-px) var(--ks-spacing-1);
                        border-radius: var(--ks-radius-xs);
                        background: var(--ks-tag-background);
                        color: var(--ks-text-primary);
                    }
                }
            }
        }

        .divider {
            height: var(--ks-border-width-thin);
            background: var(--ks-border-subtle);
            margin: var(--ks-spacing-3) 0;
        }

        .footer {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);

            .tags {
                display: flex;
                align-items: center;
                flex-wrap: wrap;
                gap: var(--ks-spacing-2);
                min-width: 0;

                .tag {
                    padding: 0.125rem 0.375rem;
                    border-radius: var(--ks-radius-sm);
                    background: var(--ks-bg-tag);
                    font-size: var(--ks-font-size-xs);
                    color: var(--ks-text-primary);
                }
            }

            .add {
                margin-left: auto;
                flex-shrink: 0;
                padding: 0 var(--ks-spacing-3);

                .plus-icon {
                    color: var(--ks-icon-muted);
                }
            }
        }
    }
</style>
