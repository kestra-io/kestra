<template>
    <div ref="container" class="blueprint-icon-stack">
        <KsTaskIcon
            v-for="cls in visible"
            :key="cls"
            class="blueprint-icon-stack__icon"
            :cls="cls"
            :icons="icons"
            onlyIcon
        />
        <span v-if="overflow > 0" class="blueprint-icon-stack__more">+{{ overflow }}</span>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onBeforeUnmount} from "vue"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import type {PluginIconMap} from "../../utils/pluginUtils"

    const props = defineProps<{
        clses: string[]
        icons: PluginIconMap
    }>()

    const ICON_PX = 24
    const GAP_PX = 8

    const parentOf = (cls: string): string => {
        const i = cls.lastIndexOf(".")
        return i === -1 ? cls : cls.substring(0, i)
    }

    const unique = computed<string[]>(() => {
        const seen = new Set<string>()
        const out: string[] = []
        for (const cls of props.clses) {
            const parent = parentOf(cls)
            if (!seen.has(parent)) {
                seen.add(parent)
                out.push(parent)
            }
        }
        return out
    })

    const container = ref<HTMLElement | null>(null)
    const width = ref(Number.POSITIVE_INFINITY)

    const fitting = computed(() => {
        if (!Number.isFinite(width.value)) return unique.value.length
        if (width.value <= 0) return 0
        return Math.max(0, Math.floor((width.value + GAP_PX) / (ICON_PX + GAP_PX)))
    })

    const visible = computed<string[]>(() => {
        if (unique.value.length <= fitting.value) return unique.value
        return unique.value.slice(0, Math.max(0, fitting.value - 1))
    })

    const overflow = computed(() => unique.value.length - visible.value.length)

    let observer: ResizeObserver | null = null
    onMounted(() => {
        if (!container.value) return
        observer = new ResizeObserver((entries) => {
            for (const e of entries) width.value = e.contentRect.width
        })
        observer.observe(container.value)
    })
    onBeforeUnmount(() => observer?.disconnect())
</script>

<style scoped lang="scss">
    .blueprint-icon-stack {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        min-width: 0;
        flex: 1 1 auto;
        overflow: hidden;

        &__icon {
            flex-shrink: 0;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            width: var(--ks-spacing-5);
            height: var(--ks-spacing-5);
            padding: 2px;
            background-color: var(--ks-bg-tag);
            border-radius: var(--ks-radius-base);
        }

        &__more {
            width: var(--ks-spacing-5);
            height: var(--ks-spacing-5);
            flex-shrink: 0;
            display: inline-flex;
            align-items: center;
            justify-content: center;
            background-color: var(--ks-bg-tag);
            border-radius: var(--ks-radius-base);
            font-size: var(--ks-font-size-2xs);
            color: var(--ks-text-primary);
        }
    }
</style>
