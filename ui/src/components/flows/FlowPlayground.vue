<template>
    <section class="playground">
        <h2>
            <ChartTimelineIcon class="tab-icon" />
            {{ t("playground.title") }} {{ playgroundStore.executionState }}
        </h2>
        <div class="content">
            <div class="current-run">
                <div class="current-run-header">
                    <div class="pillTabs">
                        <button
                            v-for="tab in tabs"
                            :key="tab.name"
                            type="button"
                            :class="[{activeTab: tab.name === activeTab.name}]"
                            @click="activeTab = tab"
                        >
                            {{ tab.title }}
                        </button>
                    </div>
                    <div class="extra-options">
                        <Kill
                            v-if="executionsStore.execution"
                            :execution="executionsStore.execution"
                        />
                    </div>
                </div>
                <div v-if="activeTab?.component && playgroundStore.latestExecution" class="tab-content">
                    <component
                        :is="activeTab.component"
                        :key="activeTab.name"
                    />
                </div>
                <div v-else class="empty-state">
                    <img :src="EmptyVisualPlayground">
                    <p>{{ t("playground.run_task_info") }}</p>
                    <p>{{ t("playground.play_icon_info") }}</p>
                </div>
            </div>
            <div class="run-history" :class="{'history-visible': historyVisible}">
                <h3><HistoryIcon class="tab-icon" />{{ t("playground.history") }}</h3>
                <PlaygroundLog :executions="playgroundStore.executions" />
            </div>
            <button class="toggle-history" @click="historyVisible = !historyVisible">
                <CloseIcon v-if="historyVisible" />
                <HistoryIcon v-else />
            </button>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, ref, markRaw, watch, onUnmounted, onMounted} from "vue";
    import {useI18n} from "vue-i18n";
    import ChartTimelineIcon from "vue-material-design-icons/ChartTimeline.vue";
    import HistoryIcon from "vue-material-design-icons/History.vue";
    import CloseIcon from "vue-material-design-icons/Close.vue";
    import Gantt from "../executions/Gantt.vue";
    import Logs from "../executions/Logs.vue";
    import ExecutionOutput from "../executions/outputs/Wrapper.vue";
    import ExecutionMetric from "../executions/ExecutionMetric.vue";
    import PlaygroundLog from "./playground/PlaygroundLog.vue";
    import {usePlaygroundStore} from "../../stores/playground";
    import EmptyVisualPlayground from "../../assets/empty_visuals/playground.svg"
    import {useExecutionsStore} from "../../stores/executions";
    import Kill from "../executions/Kill.vue";

    const {t} = useI18n();

    const tabs = computed(() => ([{
                                      name: "logs",
                                      title: t("logs"),
                                      component: markRaw(Logs),
                                  },{
                                      name: "gantt",
                                      title: t("gantt"),
                                      component: markRaw(Gantt),
                                  },
                                  {
                                      name: "outputs",
                                      title: t("outputs"),
                                      component: markRaw(ExecutionOutput),
                                  },
                                  {
                                      name: "metrics",
                                      title: t("metrics"),
                                      component: markRaw(ExecutionMetric),
                                  }
    ]));

    const playgroundStore = usePlaygroundStore();
    const executionsStore = useExecutionsStore();

    watch(() => playgroundStore.latestExecution?.id, (newValue, oldValue) => {
        if (newValue && newValue !== oldValue) {
            executionsStore.followExecution(playgroundStore.latestExecution, t);
        }
    });

    const activeTab = ref(tabs.value[0]);

    onMounted(() => {
        playgroundStore.runFromQuery();
    });

    onUnmounted(() => {
        executionsStore.closeSSE();
    });

    const historyVisible = ref(false);
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/_color-palette";

    .tab-icon{
        color: var(--ks-content-inactive);
        margin-right: 4px;
    }

    .playground {
        height: 100%;
        display: flex;
        flex-direction: column;
        position: relative;
        color: var(--ks-color-text-secondary);
        background-color: var(--ks-background-panel);
        overflow-y: auto;
        h2{
            border-bottom: 1px solid var(--ks-border-primary);
            font-size: .8rem;
            font-weight: normal;
            line-height: 1.2rem;
            padding: 0 8px 4px;
            position: sticky;
            background-color: var(--ks-background-panel);
            top: 0;
            z-index: 100;
        }
    }

    .content{
        display: flex;
        flex: 1;
        align-items: stretch;
    }

    .current-run {
        flex: 1;
    }

    .extra-options{
        display: flex;
        gap: 8px;
        margin-right: 4rem;
    }

    .toggle-history{
        position: absolute;
        top: 36px;
        right: 12px;
        background-color: var(--ks-background-card);
        border: none;
        padding: 8px;
        border-radius: 50%;
        display: flex;
        z-index: 99;
        &:hover {
            background-color: var(--ks-background-card-hover);
        }
    }

    .run-history{
        border-left: 1px solid transparent;
        width: 0;
        overflow: hidden;
        transition: all .2s ease-in-out;
        h3{
            display: flex;
            align-items: center;
            gap: .5rem;
            width: 268px;
            font-size: 1rem;
            margin: .8rem 1rem;
            font-weight: normal;
            margin-bottom: 0.5rem;
            color: var(--ks-content-primary);
        }

        &.history-visible {
            width: 300px;
            overflow-y: auto;
            border-color: var(--ks-border-primary);
        }
    }

    .current-run-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
    }

    .kill-run-button{
        margin-right: 4rem
    }

    .pillTabs {
        display: flex;
        padding: 4px;
        background-color:var(--ks-background-card) ;
        margin: 1rem;
        border-radius: 6px;
        gap: 2px;
        button{
            padding: 0.2rem .5rem;
            font-size: 14px;
            color: var(--ks-content-tertiary);
            background-color: transparent;
            border: none;
            border-radius: 4px;
            &.activeTab {
                color: $base-white;
                background-color: $base-blue-500;
            }
        }
    }

    .tab-content{
        overflow: auto;
        padding: 1rem;
        background-color: var(--ks-background-panel);
    }

    .empty-state{
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        p {
            text-align: center;
            color: var(--ks-content-secondary);
            img {
                width: 200px;
                margin-bottom: 1rem;
            }
        }
    }
</style>
