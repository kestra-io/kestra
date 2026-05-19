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
            <ChevronDown :size="14" class="ks-sidebar-section__chevron" :class="{'is-collapsed': collapsed}" />
        </button>
        <div v-else-if="title" class="ks-sidebar-section__title">{{ title }}</div>

        <div v-show="!collapsible || !collapsed" class="ks-sidebar-section__body">
            <slot />
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"

    const props = withDefaults(defineProps<{
        title?: string
        collapsible?: boolean
        defaultCollapsed?: boolean
    }>(), {
        collapsible: false,
        defaultCollapsed: false,
    })

    const emit = defineEmits<{
        toggle: [collapsed: boolean]
    }>()

    const collapsed = ref(props.defaultCollapsed)

    // When the parent flags this section as the active one (defaultCollapsed: true → false),
    // auto-expand it. Never auto-collapse — keep previously opened sections open so navigating
    // between groups doesn't close ones the user has already revealed.
    watch(() => props.defaultCollapsed, (next) => {
        if (!next) collapsed.value = false
    })

    function toggle() {
        collapsed.value = !collapsed.value
        emit("toggle", collapsed.value)
    }

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style scoped lang="scss">
.ks-sidebar-section {
    padding: 0 var(--ks-spacing-4);
}

.ks-sidebar-section__title {
    display: inline-flex;
    align-items: center;
    gap: var(--ks-spacing-1);
    padding: var(--ks-spacing-3) 0 var(--ks-spacing-1);
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
    background: none;
    border: none;
    text-align: left;
    font-family: inherit;
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
</style>
