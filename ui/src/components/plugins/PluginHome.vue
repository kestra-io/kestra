<template>
    <dotted-layout
        :embed="embed"
        :phrase="$t('pluginPage.title2')"
        :alt="$t('blueprints.header.alt')"
        :image="headerImage"
        :image-dark="headerImageDark"
    >
        <el-row class="my-4 px-3">
            <KestraFilter :placeholder="$t('pluginPage.search', {count: countPlugin})" :search-callback="(input)=> searchInput = input" />
        </el-row>
        <section class="px-3 plugins-container">
            <el-tooltip v-for="(plugin, index) in pluginsList" :show-after="1000" :key="plugin.name + '-' + index" effect="light">
                <template #content>
                    <div class="tasks-tooltips">
                        <p v-if="plugin?.tasks.filter(t => t.toLowerCase().includes(searchInput)).length > 0" class="mb-0">
                            Tasks
                        </p>
                        <ul>
                            <li
                                v-for="task in plugin.tasks.filter(t => t.toLowerCase().includes(searchInput))"
                                :key="task"
                            >
                                <span @click="openPlugin(task)">{{ task }}</span>
                            </li>
                        </ul>
                        <p v-if="plugin?.triggers.filter(t => t.toLowerCase().includes(searchInput)).length > 0" class="mb-0">
                            Triggers
                        </p>
                        <ul>
                            <li
                                v-for="trigger in plugin.triggers.filter(t => t.toLowerCase().includes(searchInput))"
                                :key="trigger"
                            >
                                <span @click="openPlugin(trigger)">{{ trigger }}</span>
                            </li>
                        </ul>
                        <p v-if="plugin?.conditions.filter(t => t.toLowerCase().includes(searchInput)).length > 0" class="mb-0">
                            Conditions
                        </p>
                        <ul>
                            <li
                                v-for="condition in plugin.conditions.filter(t => t.toLowerCase().includes(searchInput))"
                                :key="condition"
                            >
                                <span @click="openPlugin(condition)">{{ condition }}</span>
                            </li>
                        </ul>
                        <p v-if="plugin?.taskRunners.filter(t => t.toLowerCase().includes(searchInput)).length > 0" class="mb-0">
                            Task
                            Runners
                        </p>
                        <ul>
                            <li
                                v-for="taskRunner in plugin.taskRunners.filter(t => t.toLowerCase().includes(searchInput))"
                                :key="taskRunner"
                            >
                                <span @click="openPlugin(taskRunner)">{{ taskRunner }}</span>
                            </li>
                        </ul>
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
    import {TaskIcon} from "@kestra-io/ui-libs";
    import DottedLayout from "../layout/DottedLayout.vue";
    import headerImage from "../../assets/icons/plugin.svg";
    import headerImageDark from "../../assets/icons/plugin-dark.svg";
    import KestraFilter from "../filter/KestraFilter.vue";

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
                searchInput: "",
                headerImage,
                headerImageDark
            }
        },
        created() {
            this.$store.dispatch("plugin/groupIcons").then(
                res => {
                    this.icons = res
                }
            )
        },
        computed: {
            countPlugin() {
                let allTasks = [];
                let allTriggers = [];
                let allConditions = [];
                let allTaskRunners = [];

                // avoid duplicate across groups and subgroups
                this.plugins.forEach(plugin => {
                    allTasks = [...allTasks, ...plugin.tasks];
                    allTriggers = [...allTriggers, ...plugin.triggers];
                    allConditions = [...allConditions, ...plugin.conditions];
                    allTaskRunners = [...allTaskRunners, ...plugin.taskRunners];
                });

                return (new Set(allTasks)).size +
                    (new Set(allTriggers)).size +
                    (new Set(allConditions)).size +
                    (new Set(allTaskRunners)).size;
            },
            pluginsList() {
                return this.plugins
                    .filter((plugin, index, self) => {
                        return index === self.findIndex((t) => (
                            t.title === plugin.title && t.group === plugin.group
                        ));
                    })
                    .filter(plugin => {
                        return plugin.title.toLowerCase().includes(this.searchInput.toLowerCase()) ||
                            plugin.tasks.some(task => task.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.triggers.some(trigger => trigger.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.conditions.some(condition => condition.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                            plugin.taskRunners.some(taskRunner => taskRunner.toLowerCase().includes(this.searchInput.toLowerCase()))
                    })
                    .filter(plugin => this.isVisible(plugin))
                    .sort((a, b) => {
                        const nameA = a.manifest["X-Kestra-Title"].toLowerCase(),
                              nameB = b.manifest["X-Kestra-Title"].toLowerCase();

                        return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
                    })
            }
        },
        methods: {
            openGroup(plugin) {
                this.openPlugin(
                    plugin.tasks?.[0] ??
                        plugin.triggers?.[0] ??
                        plugin.conditions?.[0] ??
                        plugin.taskRunners?.[0]
                )
            },
            openPlugin(cls) {
                if (!cls) {
                    return;
                }
                this.$router.push({name: "plugins/view", params: {cls: cls}})
            },
            isVisible(plugin) {
                return [...plugin.tasks, ...plugin.triggers, ...plugin.conditions, ...plugin.taskRunners].length > 0
            },
            hasIcon(cls) {
                return this.icons[cls] !== undefined;
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

        &::-webkit-scrollbar {
            width: 5px;
        }

        &::-webkit-scrollbar-track {
            -webkit-border-radius: 10px;
        }

        &::-webkit-scrollbar-thumb {
            -webkit-border-radius: 10px;
            background: var(--ks-button-background-primary);
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

        &:hover{
            border-color: var(--ks-border-active);
            background-color: var(--ks-button-background-secondary-hover);
        }
    }

    .size {
        height: 2em;
        width: 2em;
    }
</style>