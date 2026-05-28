<template>
    <details :id="href" :open="!collapsed">
        <summary
            class="collapse-button"
            :class="{collapsed}"
            @click="handleToggle"
        >
            <span class="collapse-button__label">
                <component :is="collapsed ? MenuRight : MenuDown" v-if="arrow" class="arrow" />
                {{ clickableText }}
                <slot name="additionalButtonText" />
            </span>
            <span v-if="$slots.buttonRight" class="collapse-button__right">
                <slot name="buttonRight" :collapsed="collapsed" />
            </span>
        </summary>
        <div v-if="$slots.content" :id="`${href}-body`">
            <div>
                <slot name="content" />
            </div>
        </div>
    </details>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useBrowserLocation} from "@vueuse/core"
    import MenuRight from "vue-material-design-icons/MenuRight.vue"
    import MenuDown from "vue-material-design-icons/MenuDown.vue"

    const props = withDefaults(defineProps<{
        href?: string;
        clickableText: string;
        arrow?: boolean;
        initiallyExpanded?: boolean;
        noUrlChange?: boolean;
    }>(), {
        href: () => Math.random().toString(36).substring(2, 5),
        arrow: true,
        initiallyExpanded: false,
        noUrlChange: false,
    })

    const emit = defineEmits<{expand: []}>()

    const collapsed = ref(true)
    const location = useBrowserLocation()
    const bodyHash = computed(() => `#${props.href}-body`)

    const handleToggle = (event: Event) => {
        event.preventDefault()
        event.stopPropagation()

        collapsed.value = !collapsed.value

        if (!collapsed.value) {
            emit("expand")
        }

        if (props.noUrlChange) return

        if (collapsed.value) {
            history.replaceState(null, "", window.location.pathname + window.location.search)
        } else {
            window.location.hash = bodyHash.value
        }
    }

    watch(() => props.initiallyExpanded, (initiallyExpanded) => {
        if (initiallyExpanded !== undefined) {
            collapsed.value = !initiallyExpanded
        }
    }, {immediate: true})

    watch(() => location.value.hash, (hash) => {
        if (hash === bodyHash.value && collapsed.value) {
            collapsed.value = false
        }
    }, {immediate: true})
</script>

<style scoped lang="scss">
    details {
        overflow: hidden;
        interpolate-size: allow-keywords;
    }

    details::details-content {
        block-size: 0;
        transition: block-size 150ms ease, content-visibility 150ms;
        transition-behavior: allow-discrete;
    }

    details[open]::details-content {
        block-size: auto;
    }

    .collapse-button {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 0.5rem;
        padding: 0;
        border: none;
        background: none;
        font-weight: 700;

        &:focus {
            outline: none;
            box-shadow: none;
        }
    }

    .collapse-button__label {
        display: flex;
        align-items: center;
        gap: 0.5rem;
    }

    .collapse-button__right {
        display: flex;
        flex: 1 1 auto;
    }
</style>
