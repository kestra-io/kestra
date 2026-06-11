<template>
    <section class="ks-sidebar-section">
        <button
            v-if="title && collapsible"
            type="button"
            class="ks-sidebar-section__title is-collapsible"
            :aria-expanded="!collapsed"
            @click="toggle"
        >
            <span class="ks-sidebar-section__title-text">{{ title }}</span>
            <slot name="suffix" />
            <ChevronDown :size="14" class="ks-sidebar-section__chevron" :class="{'is-collapsed': collapsed}" />
        </button>
        <div v-else-if="title" class="ks-sidebar-section__title">
            <span class="ks-sidebar-section__title-text">{{ title }}</span>
            <slot name="suffix" />
        </div>

        <div class="ks-sidebar-section__body" :class="{'is-open': isOpen}">
            <div class="ks-sidebar-section__body-inner" :inert="isOpen ? undefined : true">
                <slot />
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    const props = withDefaults(defineProps<{
        title?: string
        collapsible?: boolean
        defaultCollapsed?: boolean
        collapsed?: boolean
    }>(), {
        collapsible: false,
        defaultCollapsed: false,
        collapsed: undefined,
    })

    const emit = defineEmits<{
        "toggle": [collapsed: boolean]
        "update:collapsed": [value: boolean]
    }>()

    const internal = ref<boolean>(props.defaultCollapsed)

    const isControlled = computed(() => props.collapsed !== undefined)
    const collapsed = computed<boolean>(() => isControlled.value ? props.collapsed as boolean : internal.value)
    const isOpen = computed<boolean>(() => !props.collapsible || !collapsed.value)

    watch(() => props.defaultCollapsed, (next) => {
        if (!isControlled.value && !next) internal.value = false
    })

    function toggle() {
        const next = !collapsed.value
        if (isControlled.value) {
            emit("update:collapsed", next)
        } else {
            internal.value = next
        }
        emit("toggle", next)
    }

    defineSlots<{
        default?(): unknown
        suffix?(): unknown
    }>()
</script>

<style scoped lang="scss">
.ks-sidebar-section {
    padding: 0 var(--ks-spacing-4);
}

.ks-sidebar-section:first-child .ks-sidebar-section__title {
    padding-top: 0;
}

.ks-sidebar-section__title {
    display: flex;
    width: 100%;
    align-items: center;
    gap: var(--ks-spacing-2);
    padding: var(--ks-spacing-3) 0 0;
    margin-bottom: var(--ks-spacing-2);
    font-size: var(--ks-font-size-xs);
    font-weight: 400;
    color: var(--ks-text-dim);
    background: none;
    border: none;
    text-align: left;
    font-family: inherit;
    box-sizing: border-box;
}

.ks-sidebar-section__title-text {
    min-width: 0;
}

.ks-sidebar-section__title.is-collapsible {
    cursor: pointer;

    &:hover {
        color: var(--ks-text-primary);
    }
}

.ks-sidebar-section__chevron {
    color: inherit;
    transition: transform 0.15s ease;

    &.is-collapsed {
        transform: rotate(-90deg);
    }
}

.ks-sidebar-section__body {
    display: grid;
    grid-template-rows: 0fr;
    transition: grid-template-rows 0.25s cubic-bezier(0.22, 1, 0.36, 1);

    &.is-open {
        grid-template-rows: 1fr;
    }
}

.ks-sidebar-section__body-inner {
    min-height: 0;
    overflow: hidden;
}
</style>
