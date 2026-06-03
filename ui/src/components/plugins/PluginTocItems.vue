<template>
    <ul class="plugin-toc-elements">
        <li
            v-for="el in allElements"
            :key="el.cls"
            class="plugin-toc-elements__item"
        >
            <router-link
                class="plugin-toc-elements__link"
                :class="{'plugin-toc-elements__link--active': route.params.cls === el.cls}"
                :to="{name: 'plugins/view', params: {tenant: route.params.tenant, cls: el.cls}}"
                @click="emit('navigate')"
            >
                <KsTaskIcon
                    class="plugin-toc-elements__icon"
                    :onlyIcon="true"
                    :cls="el.cls"
                    :icons="icons"
                />
                <span class="plugin-toc-elements__label">{{ shortName(el.cls) }}</span>
            </router-link>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import type {PluginElement, PluginIconMap} from "../../utils/pluginUtils"

    const props = defineProps<{
        groupedElements: Record<string, PluginElement[]>;
        icons: PluginIconMap;
    }>()

    const emit = defineEmits<{
        navigate: [];
    }>()

    const route = useRoute()

    const allElements = computed<PluginElement[]>(() => {
        const flat = Object.values(props.groupedElements).flat()
        const seen = new Set<string>()
        const unique: PluginElement[] = []
        for (const el of flat) {
            if (!seen.has(el.cls)) {
                seen.add(el.cls)
                unique.push(el)
            }
        }
        return unique.sort((a, b) => shortName(a.cls).localeCompare(shortName(b.cls)))
    })

    const shortName = (cls: string): string => {
        const lastDot = cls.lastIndexOf(".")
        return lastDot === -1 ? cls : cls.substring(lastDot + 1)
    }
</script>

<style scoped lang="scss">
    .plugin-toc-elements {
        list-style: none;
        padding: 0;
        margin: 0;
        display: flex;
        flex-direction: column;
        gap: 2px;
        padding: var(--ks-spacing-1) var(--ks-spacing-4) ;
    }

    .plugin-toc-elements__item {
        margin: 0;
    }

    .plugin-toc-elements__link {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-base);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
        text-decoration: none;
        line-height: 1.25rem;

        &:hover {
            background-color: var(--ks-bg-tag);
            color: var(--ks-text-primary);
        }

        &--active {
            background-color: var(--ks-bg-active);
            color: var(--ks-text-primary);
            font-weight: 600;
        }
    }

    .plugin-toc-elements__icon {
        flex-shrink: 0;
        width: 1rem;
        height: 1rem;
        background-color: var(--ks-bg-tag);
        border-radius: var(--ks-radius-xs);
    }

    .plugin-toc-elements__label {
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        min-width: 0;
    }
</style>
