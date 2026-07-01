<template>
    <!-- Inside <KsSteps variant="bar">: render as a single label-less progress segment. -->
    <span
        v-if="isBar"
        class="ks-stepbar__seg"
        :class="`is-${segState}`"
        role="listitem"
        :aria-label="title || undefined"
        :aria-current="segState === 'active' ? 'step' : undefined"
    />
    <ElStep
        v-else
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template v-if="$slots.icon" #icon>
            <slot name="icon" />
        </template>
        <template v-else-if="icon" #icon>
            <ElIcon class="kel-step__icon-inner kel-step-icon--main">
                <component :is="icon" />
            </ElIcon>
            <ElIcon class="kel-step__icon-inner kel-step-icon--success">
                <CheckBold />
            </ElIcon>
        </template>
        <template v-if="$slots.title" #title>
            <slot name="title" />
        </template>
        <template v-if="$slots.description" #description>
            <slot name="description" />
        </template>
    </ElStep>
</template>

<script setup lang="ts">
    import {ElStep, ElIcon} from "element-plus"
    import CheckBold from "vue-material-design-icons/CheckBold.vue"
    import {inject, computed, type Ref} from "vue"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        title?: string
        description?: string
        icon?: any
        status?: string
    }>()

    const filteredProps = useFilteredProps(props)

    // Provided by a parent KsSteps. In the "bar" variant this step is a progress segment whose state
    // comes from its own `status`: process -> active, success/finish -> filled, anything else -> upcoming.
    const ksStepsVariant = inject<Ref<"steps" | "bar" | undefined> | undefined>("ksStepsVariant", undefined)
    const isBar = computed(() => ksStepsVariant?.value === "bar")
    const segState = computed(() =>
        props.status === "process"
            ? "active"
            : (props.status === "success" || props.status === "finish") ? "filled" : "upcoming",
    )

    defineSlots<{
        default?(): unknown
        icon?(): unknown
        title?(): unknown
        description?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/step';

    .kel-steps {
        --ks-step-badge-size: 2.25rem;
        --ks-step-icon-size: var(--ks-spacing-5);
        --ks-step-title-size: var(--ks-font-size-lg);

        &--small {
            --ks-step-badge-size: 1.75rem;
            --ks-step-icon-size: var(--ks-spacing-4);
            --ks-step-title-size: var(--ks-font-size-md);
        }

        .kel-step__icon {
            width: var(--ks-step-badge-size) !important;
            height: var(--ks-step-badge-size) !important;
            border-radius: 50%;
            border: 1px solid var(--ks-border-default);
            background: var(--ks-bg-inactive);
            color: var(--ks-icon-inactive);
            box-shadow: 0 1px 2px var(--ks-shadow-element);
        }

        .kel-step__icon:empty {
            display: none;
        }

        .kel-step__icon .material-design-icon,
        .kel-step__icon .material-design-icon__svg {
            width: var(--ks-step-icon-size);
            height: var(--ks-step-icon-size);
        }

        .kel-step__icon-inner[class*="kel-icon"]:not(.is-status) {
            font-size: var(--ks-step-icon-size);
        }

        .is-process .kel-step__icon {
            border-color: var(--ks-border-focus);
            background: var(--ks-bg-overlay);
            color: var(--ks-icon-active);
            box-shadow: 0 8px 12px var(--ks-shadow-elevated);
        }

        .is-finish .kel-step__icon,
        .is-success .kel-step__icon {
            border-color: var(--ks-border-success);
            background: var(--ks-bg-overlay);
            color: var(--ks-icon-success);
            box-shadow: 0 8px 12px var(--ks-shadow-elevated);
        }

        .kel-step-icon--success {
            display: none;
        }

        .is-finish,
        .is-success {
            .kel-step-icon--main {
                display: none;
            }

            .kel-step-icon--success {
                display: inline-flex;
            }
        }

        .kel-step__title {
            font-size: var(--ks-step-title-size);
            font-weight: var(--ks-font-weight-semibold);
            color: var(--ks-text-inactive);

            &.is-process,
            &.is-finish,
            &.is-success {
                color: var(--ks-text-primary);
            }
        }

        .kel-step__description {
            &.is-finish,
            &.is-success {
                color: var(--ks-text-success);
            }
        }

        .kel-step__line-inner {
            display: none;
        }

        .kel-step.is-vertical {
            .kel-step__line {
                left: calc(var(--ks-step-badge-size) / 2);
                background: transparent;
                border-left: 1px dashed var(--ks-border-strong);
            }

            .kel-step__main {
                padding-left: var(--ks-spacing-4);
            }
        }

        .kel-step.is-horizontal .kel-step__line {
            top: calc(var(--ks-step-badge-size) / 2);
            background: transparent;
            border-top: 1px dashed var(--ks-border-strong);
        }
    }
</style>
