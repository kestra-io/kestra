<template>
    <el-row justify="center" align="middle" class="headband-row">
        <el-col justify="center">
            <p class="fw-lighter fs-5 text-center text-truncate">
                {{ $t("pluginPage.title1") }}
            </p>
            <p class="fw-bold fs-5 text-center text-truncate">
                {{ $t("pluginPage.title2") }}
            </p>
        </el-col>
    </el-row>
    <el-row justify="center" class="my-4">
        <el-input
            class="search"
            :placeholder="$t('pluginPage.search', {count: countPlugin})"
            v-model="searchInput"
            clearable
        />
    </el-row>
    <div class="plugins-container">
        <el-tooltip v-for="plugin in pluginsList" :key="plugin.title">
            <template #content>
                <div class="tasks-tooltips">
                    <p v-if="plugin?.tasks.filter(t => t.toLowerCase().includes(searchInput)).length > 0">
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
                    <p v-if="plugin?.triggers.filter(t => t.toLowerCase().includes(searchInput)).length > 0">
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
                    <p v-if="plugin?.conditions.filter(t => t.toLowerCase().includes(searchInput)).length > 0">
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
                    <p v-if="plugin?.taskRunners.filter(t => t.toLowerCase().includes(searchInput)).length > 0">
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
                <task-icon class="size" :only-icon="true" :cls="plugin.group" :icons="icons" />
                <span>{{ plugin.title.capitalize() }}</span>
            </div>
        </el-tooltip>
    </div>
</template>

<script>
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";

    export default {
        props: {
            plugins: {
                type: Array,
                required: true
            }
        },
        components: {
            TaskIcon
        },
        data() {
            return {
                icons: [],
                searchInput: ""
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
                return this.plugins.reduce((acc, plugin) => {
                    return acc + plugin.tasks.length + plugin.triggers.length + plugin.conditions.length + plugin.taskRunners.length
                }, 0)
            },
            pluginsList() {
                return this.plugins.filter(plugin => {
                    return plugin.title.toLowerCase().includes(this.searchInput.toLowerCase()) ||
                        plugin.tasks.some(task => task.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                        plugin.triggers.some(trigger => trigger.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                        plugin.conditions.some(condition => condition.toLowerCase().includes(this.searchInput.toLowerCase())) ||
                        plugin.taskRunners.some(taskRunner => taskRunner.toLowerCase().includes(this.searchInput.toLowerCase()))
                }).sort((a, b) => {
                    const nameA = a.group.toLowerCase(),
                          nameB = b.group.toLowerCase();

                    return (nameA < nameB ? -1 : (nameA > nameB ? 1 : 0));
                })

            }
        },
        methods: {
            openGroup(plugin) {
                if (plugin.tasks.length > 0) {
                    this.openPlugin(plugin.tasks[0])
                }
            },
            openPlugin(cls) {
                this.$router.push({name: "plugins/view", params: {cls: cls}})
            }

        }
    }
</script>

<style scoped lang="scss">
    .headband-row {
        width: 100%;
        background: url("../../assets/plugins/headband.svg") no-repeat center;
        background-size: cover;
        height: 9em;
    }

    .search {
        display: flex;
        width: 22rem;
        padding: 0.25rem 1rem;
        justify-content: center;
        align-items: center;
        gap: 0.25rem;
    }

    .plugins-container {
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
        justify-content: center;
    }

    .tasks-tooltips {
        max-height: 20rem;
        overflow-y: auto;
        overflow-x: hidden;

        span {
            cursor: pointer;
        }

        &.enhance-readability {
            padding: calc(var(--spacer) * 1.5);
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
            background: var(--bs-primary);
        }
    }

    .plugin-card {
        display: flex;
        width: 232px;
        min-width: 130px;
        padding: 8px 16px;
        align-items: center;
        gap: 8px;
        border-radius: 4px;
        border: 1px solid #404559;
        background-color: var(--bs-tertiary);
        color: var(--text-color-primary);
        text-overflow: ellipsis;
        font-size: 12px;
        font-weight: 700;
        line-height: 26px;
        cursor: pointer;
    }

    .size {
        height: 2em;
        width: 2em;
    }
</style>