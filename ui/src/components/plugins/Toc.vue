<template>
    <div class="plugins-list">
        <KsInput
            class="p-2 bg-transparent search"
            :placeholder="$t('pluginPage.search', {count: countPlugin})"
            v-model="searchInput"
            clearable
        />
        <KsCollapse accordion v-model="activeNames">
            <template v-for="(plugin) in sortedPlugins(pluginsList)" :key="plugin.title">
                <KsCollapseItem
                    v-if="isVisible(plugin)"
                    :name="plugin.group"
                    :title="plugin.title?.capitalize()"
                    :key="plugin.group"
                    :ref="(el: any) => pluginRefs[plugin.group] = el"
                >
                    <ul class="toc-h3">
                        <li v-for="(types, namespace) in group(plugin)" :key="namespace">
                            <h6>{{ namespace }}</h6>
                            <ul class="toc-h4">
                                <li v-for="(classes, type) in types" :key="type + '-' + namespace">
                                    <h6>{{ cap(type) }}</h6>
                                    <ul class="section-nav toc-h5">
                                        <li v-for="cls in classes" :key="cls">
                                            <router-link
                                                @click="$emit('routerChange'); handlePluginChange(namespace)"
                                                :to="{name: 'plugins/view', params: {cls: namespace + '.' + cls}}"
                                            >
                                                <div class="icon">
                                                    <KsTaskIcon
                                                        :onlyIcon="true"
                                                        :cls="namespace + '.' + cls"
                                                        :icons="pluginsStore.icons"
                                                    />
                                                </div>
                                                <span
                                                    :class="route.params.cls === (namespace + '.' + cls) ? 'selected mx-2' : 'mx-2'"
                                                >{{
                                                    cls
                                                }}</span>
                                            </router-link>
                                        </li>
                                    </ul>
                                </li>
                            </ul>
                        </li>
                    </ul>
                </KsCollapseItem>
            </template>
        </KsCollapse>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch, nextTick, reactive} from "vue"
    import {useRoute} from "vue-router"
    import {isEntryAPluginElementPredicate, type Plugin, type PluginElement} from "../../utils/pluginUtils"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import {usePluginsStore} from "../../stores/plugins"
    import {cap} from "../../utils/filters"

    const props = defineProps<{
        plugins: Plugin[];
    }>()

    defineEmits<{
        routerChange: [];
    }>()

    const route = useRoute()
    const pluginsStore = usePluginsStore()

    const pluginRefs = reactive<Record<string, any>>({})
    const activeNames = ref<string[]>([])
    const searchInput = ref<string>("")

    const countPlugin = computed(() => {
        return new Set(props.plugins.flatMap(plugin => pluginElements(plugin))).size
    })

    const pluginElements = (plugin: Plugin) => {
        return Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .flatMap(([_, value]) => (value as PluginElement[])
                .filter(({deprecated}) => !deprecated)
                .map(({cls}) => cls),
            )
    }

    const getScrollableParent = (el: HTMLElement): HTMLElement | null => {
        let parent = el.parentElement
        while (parent) {
            const style = getComputedStyle(parent)
            if (style.overflowY === "auto" || style.overflowY === "scroll") {
                return parent
            }
            parent = parent.parentElement
        }
        return null
    }

    const scrollToActivePlugin = () => {
        const activePlugin = localStorage.getItem("activePlugin")
        if (activePlugin) {
            const pluginElement = pluginRefs[activePlugin]
            if (pluginElement) {
                const el = pluginElement.$el as HTMLElement
                const scrollContainer = getScrollableParent(el)
                if (scrollContainer) {
                    const elRect = el.getBoundingClientRect()
                    const containerRect = scrollContainer.getBoundingClientRect()
                    scrollContainer.scrollBy({top: elRect.top - containerRect.top, behavior: "smooth"})
                }
            }
        }
    }

    const pluginsList = computed(() => {
        return props.plugins
            .filter((plugin, index, self) => {
                return index === self.findIndex((t) => (
                    t.title === plugin.title && t.group === plugin.group
                ))
            })
            .filter(plugin => {
                return plugin.title?.toLowerCase().includes(searchInput.value.toLowerCase()) ||
                    pluginElements(plugin).some(element => element.toLowerCase().includes(searchInput.value.toLowerCase()))
            })
            .map(plugin => {
                return {
                    ...plugin,
                    ...Object.fromEntries(
                        Object.entries(plugin)
                            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
                            .map(([elementType, elements]) => [
                                elementType,
                                (elements as PluginElement[]).filter(({deprecated}) => !deprecated)
                                    .filter(({cls}) => cls.toLowerCase().includes(searchInput.value.toLowerCase())),
                            ]),
                    ),
                }
            })
    })

    watch(() => route.params.cls, (cls) => {
        if (!cls) return
        props.plugins.forEach(plugin => {
            if (Object.entries(plugin).some(([key, value]) => {
                if (isEntryAPluginElementPredicate(key, value)) {
                    return (value as PluginElement[]).some(({cls: c}) => c === cls)
                }
                return false
            })) {
                if (activeNames.value[0] !== plugin.group) {
                    activeNames.value = [plugin.group]
                    localStorage.setItem("activePlugin", plugin.group)
                }
            }
        })
        nextTick(() => {
            scrollToActivePlugin()
        })
    }, {immediate: true})

    const handlePluginChange = (pluginGroup: string) => {
        activeNames.value = [pluginGroup]
        localStorage.setItem("activePlugin", pluginGroup)
    }

    const sortedPlugins = (plugins: Plugin[]) => {
        return plugins
            .sort((a, b) => {
                const nameA = (a.title ? a.title.toLowerCase() : ""),
                      nameB = (b.title ? b.title.toLowerCase() : "")

                return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0))
            })
    }

    const group = (plugin: Plugin) => {
        return Object.entries(plugin)
            .filter(([key, value]) => isEntryAPluginElementPredicate(key, value))
            .flatMap(([type, value]) => {
                return (value as PluginElement[]).filter(({deprecated}) => !deprecated)
                    .map(({cls}) => {
                        const namespace = cls.substring(0, cls.lastIndexOf("."))

                        return {
                            type,
                            namespace: namespace,
                            cls: cls.substring(cls.lastIndexOf(".") + 1),
                        }
                    })
            })
            .reduce((accumulator, value) => {
                accumulator[value.namespace] = accumulator[value.namespace] || {}
                accumulator[value.namespace][value.type] = accumulator[value.namespace][value.type] || []
                accumulator[value.namespace][value.type].push(value.cls)

                return accumulator
            }, {} as Record<string, Record<string, string[]>>)
    }

    const isVisible = (plugin: Plugin) => {
        return pluginElements(plugin).length > 0
    }
</script>

<style lang="scss" scoped>

    .plugins-list {
        display: flex;
        flex-direction: column;
        overflow-y: auto;
        height: 100%;

        .search {
            flex-shrink: 0;
            background-color: var(--ks-bg-surface);
            padding-bottom: 0.5rem;
        }

        .kel-collapse {
            flex: 1;
        }

        &.enhance-readability {
            padding: 1.5rem;
            background-color: var(--ks-bg-tag);
        }

        .kel-collapse-item__header {
            font-size: var(--ks-font-size-sm);
        }

        ul {
            list-style: none;
            padding-inline-start: 0;
            margin-bottom: 0;
            font-size: var(--ks-font-size-xs);
            margin-left: .5rem;
        }

        h6,
        a {
            word-break: break-all;
            color: var(--kel-collapse-header-text-color);
        }

        .toc-h3 {
            .icon {
                width: var(--ks-font-size-sm);
                height: var(--ks-font-size-sm);
                display: inline-block;
                position: relative;
            }

            h6 {
                font-size: 1.1em;
            }

            .toc-h4 {
                margin-left: .5rem;

                h6 {
                    font-size: var(--ks-font-size-sm);
                    margin-bottom: .5rem;
                }

                li {
                    margin-bottom: .5rem;
                }
            }
        }
    }

    .selected {
        color: var(--ks-text-link);
    }

    @media (max-width: 991px) {
        .plugins-list {
            .search {
                position: sticky;
                top: 0;
                z-index: 10;
            }

            .kel-collapse {
                overflow-y: auto;
            }

            .kel-collapse-item__header {
                font-size: var(--ks-font-size-xs);
            }

            ul {
                font-size: 0.6875rem;
                margin-left: .25rem;
            }

            .toc-h3 {
                .icon {
                    width: 0.875rem;
                    height: 0.875rem;
                }

                h6 {
                    font-size: var(--ks-font-size-xs);
                }

                .toc-h4 {
                    margin-left: .25rem;

                    h6 {
                        font-size: 0.6875rem;
                        margin-bottom: .25rem;
                    }

                    li {
                        margin-bottom: .25rem;
                    }
                }
            }
        }
    }

</style>