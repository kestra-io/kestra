<template>
    <dotted-layout
        :embed="embed"
        :phrase="$t('pluginPage.title2')"
        :alt="$t('pluginPage.alt')"
        :image="headerImage"
        :image-dark="headerImageDark"
    >
        <el-row class="my-4 px-3">
            <KestraFilter
                :placeholder="$t('pluginPage.search',
                                 {count: countPlugin})"
                legacy-query
            />
        </el-row>
        <section class="px-3 plugins-container">
            <el-tooltip
                v-for="(plugin, index) in pluginsList"
                :show-after="1000"
                :key="plugin.name + '-' + index"
                effect="light"
            >
                <template #content>
                    <div class="tasks-tooltips">
                        <template
                            v-for="([elementType, elements]) in allElementsByTypeEntries(plugin)"
                            :key="elementType"
                        >
                            <p
                                v-if="elements.filter(t => t.toLowerCase().includes(searchInput)).length > 0"
                                class="mb-0"
                            >
                                {{ $t(elementType) }}
                            </p>
                            <ul>
                                <li
                                    v-for="element in elements.filter(t => t.toLowerCase().includes(searchInput))"
                                    :key="element"
                                >
                                    <span @click="openPlugin(element)">{{ element }}</span>
                                </li>
                            </ul>
                        </template>
                    </div>
                </template>
                <div class="plugin-card" @click="openGroup(plugin)">
                    <task-icon
                        class="size"
                        :only-icon="true"
                        :cls="hasIcon(plugin.subGroup) ? plugin.subGroup : plugin.group"
                        :icons="icons"
                    />
                    <span class="text-truncate">{{ plugin.title.capitalize() }}</span>
                </div>
            </el-tooltip>
        </section>
    </dotted-layout>
</template>

<script>
    import {isEntryAPluginElementPredicate, TaskIcon} from "@kestra-io/ui-libs";
    import DottedLayout from "../layout/DottedLayout.vue";
    import headerImage from "../../assets/icons/plugin.svg";
    import headerImageDark from "../../assets/icons/plugin-dark.svg";
    import KestraFilter from "../filter/KestraFilter.vue";
    import {mapStores} from "pinia";
    import {usePluginsStore} from "../../stores/plugins";

    export default {
        props: {
            plugins: {
                type: Array,
                required: true
            },
            embed: {
                type: Boolean,
                default: false
            }
        },
        components: {
            DottedLayout,
            TaskIcon,
            KestraFilter
        },
        data() {
            return {
                icons: [],
                headerImage,
                headerImageDark
            }
        },
        created() {
            this.pluginsStore.groupIcons().then(
                res => {
                    this.icons = res
                }
            )
        },
        computed: {
            ...mapStores(usePluginsStore),
            searchInput() {
                return this.$route.query?.q?.toLowerCase() ?? "";
            },
            countPlugin() {
                return new Set(this.plugins.flatMap(plugin => this.allElements(plugin))).size;
            },
            pluginsList() {
                return this.plugins
                    .filter((plugin, index, self) => {
                        return index === self.findIndex((t) => (
                            t.title === plugin.title && t.group === plugin.group
                        ));
                    })
                    .filter(plugin =>
                        plugin.title.toLowerCase().includes(this.searchInput)
                        || this.allElements(plugin).some(e => e.toLowerCase().includes(this.searchInput))
                    ).filter(plugin => this.isVisible(plugin))
                    .sort((a, b) => {
                        const nameA = a.manifest["X-Kestra-Title"].toLowerCase(),
                              nameB = b.manifest["X-Kestra-Title"].toLowerCase();

                        return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
                    })
            }
        },
        methods: {
            openGroup(plugin) {
                const defaultElement = Object.entries(plugin)
                    .filter(([elementType, elements]) => isEntryAPluginElementPredicate(elementType, elements))
                    .flatMap(([, elements]) => elements.filter(({deprecated}) => !deprecated).map(({cls}) => cls))?.[0];
                this.openPlugin(defaultElement);
            },
            openPlugin(cls) {
                if (!cls) {
                    return;
                }
                this.$router.push({name: "plugins/view", params: {cls: cls}})
            },
            isVisible(plugin) {
                return this.allElements(plugin).length > 0;
            },
            hasIcon(cls) {
                return this.icons[cls] !== undefined;
            },
            allElementsByTypeEntries(plugin) {
                return Object.entries(plugin).filter(([elementType, elements]) => isEntryAPluginElementPredicate(elementType, elements))
                    .map(([elementType, elements]) => [
                        elementType,
                        elements.filter(({deprecated}) => !deprecated).map(({cls}) => cls)
                    ]);
            },
            allElements(plugin) {
                return this.allElementsByTypeEntries(plugin).flatMap(([_, elements]) => elements);
            }
        }
    }
</script>

<style scoped lang="scss">
    .search {
        display: flex;
        width: 22rem;
        padding: 0.25rem 2rem;
        justify-content: center;
        align-items: center;
        gap: 0.25rem;
        background-color: transparent;
    }

    .plugins-container {
        display: grid;
        gap: 16px;
        grid-template-columns: repeat(auto-fill, minmax(232px, 1fr));
        padding-bottom: 4rem;
    }

    .tasks-tooltips {
        max-height: 20rem;
        overflow-y: auto;
        overflow-x: hidden;

        span {
            cursor: pointer;
        }

        &.enhance-readability {
            padding: 1.5rem;
            background-color: var(--bs-gray-100);
        }
    }

    .plugin-card {
        display: flex;
        width: 100%;
        min-width: 130px;
        padding: 8px 16px;
        align-items: center;
        gap: 8px;
        border-radius: 4px;
        text-overflow: ellipsis;
        font-size: 12px;
        font-weight: 700;
        line-height: 26px;
        cursor: pointer;

        border: 1px solid var(--ks-border-primary);
        background-color: var(--ks-button-background-secondary);
        color: var(--ks-content-primary);

        &:hover {
            border-color: var(--ks-border-active);
            background-color: var(--ks-button-background-secondary-hover);
        }
    }

    .size {
        height: 2em;
        width: 2em;
    }
</style>
