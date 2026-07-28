<template>
    <article
        class="plugin-card"
        :class="{'plugin-card--clickable': clickable}"
        :role="clickable ? 'button' : undefined"
        :tabindex="clickable ? 0 : undefined"
        @click="onClick"
        @keydown.enter="onClick"
        @keydown.space.prevent="onClick"
    >
        <header class="plugin-card__header">
            <div v-if="hasIcon" class="plugin-card__logo">
                <slot name="icon">
                    <TaskIcon
                        v-if="iconCls"
                        :cls="iconCls"
                        :icons="icons"
                        :loadIcon="loadIcon"
                        onlyIcon
                    />
                </slot>
            </div>

            <div class="plugin-card__heading">
                <h5 class="plugin-card__title">{{ title }}</h5>
                <p v-if="description" class="plugin-card__description">
                    {{ description }}
                </p>
            </div>
        </header>

        <div v-if="categories?.length" class="plugin-card__tags">
            <KsTag v-for="category in categories" :key="category">
                <span class="plugin-card__category">{{ category }}</span>
            </KsTag>
        </div>

        <hr v-if="hasDivider" class="plugin-card__divider">

        <footer v-if="hasFooter" class="plugin-card__footer">
            <slot name="footer-content">
                <span v-if="hasTaskCount" class="plugin-card__count">
                    <span class="plugin-card__count-value">{{ taskCount }}</span>
                    <span class="plugin-card__count-label">{{ t("plugin_card.tasks", taskCount ?? 0) }}</span>
                </span>
                <span v-if="hasBlueprintCount" class="plugin-card__count">
                    <span class="plugin-card__count-value">{{ blueprintCount }}</span>
                    <span class="plugin-card__count-label">{{ t("plugin_card.blueprints", blueprintCount ?? 0) }}</span>
                </span>
            </slot>
            <ChevronRight
                v-if="clickable"
                class="plugin-card__chevron"
                aria-hidden="true"
            />
        </footer>
    </article>
</template>

<script setup lang="ts">
    import {computed, useSlots} from "vue"
    import {useI18n} from "vue-i18n"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import {KsTag} from "@kestra-io/design-system"
    import TaskIcon, {type TaskIconData} from "./TaskIcon.vue"
    import locale from "./PluginCard.locale"

    defineOptions({
        name: "PluginCard",
    })

    const {t} = useI18n({
        useScope: "local",
        inheritLocale: true,
        messages: locale,
    })
    const slots = useSlots()

    const props = withDefaults(defineProps<{
        iconCls?: string
        icons?: Record<string, any>
        loadIcon?: (cls: string) => Promise<TaskIconData | undefined>
        title: string
        description?: string | null
        categories?: string[]
        taskCount?: number | null
        blueprintCount?: number | null
        clickable?: boolean
    }>(), {
        iconCls: undefined,
        icons: () => ({}),
        loadIcon: undefined,
        description: null,
        categories: () => [],
        taskCount: null,
        blueprintCount: null,
        clickable: true,
    })

    const emit = defineEmits<{
        click: [event: Event]
    }>()

    defineSlots<{
        icon?(): unknown
        "footer-content"?(): unknown
    }>()

    const hasIcon = computed(() => Boolean(props.iconCls) || Boolean(slots.icon))
    const hasTaskCount = computed(() => typeof props.taskCount === "number" && props.taskCount > 0)
    const hasBlueprintCount = computed(() => typeof props.blueprintCount === "number" && props.blueprintCount > 0)
    const hasCounts = computed(() => hasTaskCount.value || hasBlueprintCount.value)
    const hasDivider = computed(() => hasCounts.value || Boolean(slots["footer-content"]))
    const hasFooter = computed(() => hasDivider.value || props.clickable)

    function onClick(event: Event) {
        if (!props.clickable) return
        emit("click", event)
    }
</script>

<style scoped lang="scss">
    .plugin-card {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-4) var(--ks-spacing-4) var(--ks-spacing-2);
        background-color: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 2px 4px var(--ks-shadow-surface);
        color: var(--ks-text-primary);
        transition: border-color 0.15s ease, box-shadow 0.15s ease;

        &--clickable {
            cursor: pointer;

            &:hover {
                border-color: var(--ks-border-focus);
                box-shadow: 0 4px 8px var(--ks-shadow-surface);
            }

            &:focus-visible {
                outline: 2px solid var(--ks-border-focus);
                outline-offset: 2px;
            }
        }

        &__header {
            display: flex;
            align-items: flex-start;
            gap: var(--ks-spacing-4);
            width: 100%;
        }

        &__logo {
            flex-shrink: 0;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: 2.625rem;
            height: 2.625rem;
            padding: var(--ks-spacing-1);
            background-color: var(--ks-bg-tag);
            border-radius: var(--ks-radius-base);
        }

        &__heading {
            display: flex;
            flex-direction: column;
            gap: var(--ks-spacing-1);
            flex: 1 1 0;
            min-width: 0;
        }

        &__title {
            margin: 0;
            font-size: var(--ks-font-size-md);
            font-weight: 600;
            line-height: 1.125rem;
            color: var(--ks-text-primary);
            white-space: nowrap;
            overflow: hidden;
            text-overflow: ellipsis;
        }

        &__description {
            margin: 0;
            font-size: var(--ks-font-size-xs);
            font-weight: 400;
            line-height: 1.25rem;
            color: var(--ks-text-secondary);
            display: -webkit-box;
            line-clamp: 2;
            -webkit-line-clamp: 2;
            -webkit-box-orient: vertical;
            overflow: hidden;
            height: 2.5rem;
        }

        &__tags {
            display: flex;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
        }

        &__category {
            display: inline-block;
            text-transform: lowercase;

            &::first-letter {
                text-transform: uppercase;
            }
        }

        &__divider {
            margin: 0;
            border: 0;
            border-top: 1px solid var(--ks-border-default);
            width: 100%;
        }

        &__footer {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-4);
        }

        &__count {
            display: inline-flex;
            align-items: center;
            gap: var(--ks-spacing-1);
            line-height: 0.875rem;
            white-space: nowrap;
        }

        &__count-value {
            font-size: var(--ks-font-size-xs);
            font-weight: 600;
            color: var(--ks-text-primary);
        }

        &__count-label {
            font-size: var(--ks-font-size-xs);
            font-weight: 400;
            color: var(--ks-text-secondary);
        }

        &__chevron {
            margin-left: auto;
        }
    }
</style>
