<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <collapse>
            <el-form-item>
                <el-input
                    v-model="filter"
                    @update:model-value="onChange"
                    :placeholder="$t('search')"
                >
                    <template #suffix>
                        <magnify />
                    </template>
                </el-input>
            </el-form-item>
            <el-form-item>
                <log-level-selector
                    v-model="level"
                    @update:model-value="onChange"
                />
            </el-form-item>
            <el-form-item v-for="logLevel in currentLevelOrLower" :key="logLevel">
                <log-level-navigator
                    v-if="countByLogLevel[logLevel] > 0"
                    :cursor-idx="cursorLogLevel === logLevel ? cursorIdxForLevel : undefined"
                    :level="logLevel"
                    :total-count="countByLogLevel[logLevel]"
                    @previous="previousLogForLevel(logLevel)"
                    @next="nextLogForLevel(logLevel)"
                    @close="clearLogLevel(logLevel)"
                />
            </el-form-item>
            <el-form-item>
                <el-button @click="expandCollapseAll()">
                    {{ logDisplayButtonText }}
                </el-button>
            </el-form-item>
            <el-form-item>
                <el-tooltip
                    :content="!raw_view ? $t('logs_view.raw_details') : $t('logs_view.compact_details')"
                >
                    <el-button @click="setRawView()">
                        {{ !raw_view ? $t('logs_view.raw') : $t('logs_view.compact') }}
                    </el-button>
                </el-tooltip>
            </el-form-item>
            <el-form-item>
                <el-button-group class="min-w-auto">
                    <restart :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
                    <el-button @click="downloadContent()">
                        <kicon :tooltip="$t('download logs')">
                            <download />
                        </kicon>
                    </el-button>
                </el-button-group>
            </el-form-item>
        </collapse>

        <task-run-details
            v-if="!raw_view"
            ref="logs"
            :logs="Array.isArray(logs) ? logs : []"
            :level="level"
            :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
            :filter="filter"
            :level-to-highlight="cursorLogLevel"
            @log-cursor="logCursor = $event"
            :log-cursor="logCursor"
            @follow="forwardEvent('follow', $event)"
            @opened-taskruns-count="openedTaskrunsCount = $event"
            @fetch-logs="fetchLogs(executionId)"
            @log-indices-by-level="Object.entries($event).forEach(([levelName, indices]) => logIndicesByLevel[levelName] = indices)"
            :target-execution="execution"
            :target-flow="flow"
            :show-progress-bar="false"
        />
        <el-card v-else class="attempt-wrapper">
            <DynamicScroller
                ref="logScroller"
                :items="temporalLogs"
                :min-item-size="getTemporalLogsSize()"
                key-field="uniqueId"
                class="log-lines"
                :buffer="200"
                :prerender="20"
                :key="filterUpdateCount"
            >
                <template #default="{item, index, active}">
                    <DynamicScrollerItem
                        :item="item"
                        :active="active"
                        :size-dependencies="[item.message]"
                        :data-index="index"   
                        :ref="el => setLogLineRef(index, el)"  
                    >   
                        <log-line   
                            @click="temporalCursor = index.toString()"
                            class="line"
                            :class="{['log-bg-' + cursorLogLevel?.toLowerCase()]: cursorLogLevel === item.level, 'opacity-40': cursorLogLevel && cursorLogLevel !== item.level}"
                            :cursor="temporalCursor!==undefined && item.index == temporalCursor"
                            :level="level"
                            :filter="filter"
                            :log="item"
                            title              
                        />
                    </DynamicScrollerItem>
                </template>
            </DynamicScroller>
        </el-card>
    </div>
</template>

<script>
    import TaskRunDetails from "../logs/TaskRunDetails.vue";
    import {mapState} from "vuex";
    import Download from "vue-material-design-icons/Download.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Kicon from "../Kicon.vue";
    import LogLevelSelector from "../logs/LogLevelSelector.vue";
    import LogLevelNavigator from "../logs/LogLevelNavigator.vue";
    import {DynamicScroller, DynamicScrollerItem} from "vue-virtual-scroller";
    import "vue-virtual-scroller/dist/vue-virtual-scroller.css"
    import Collapse from "../layout/Collapse.vue";
    import State from "../../utils/state";
    import Utils from "../../utils/utils";
    import LogLine from "../logs/LogLine.vue";
    import Restart from "./Restart.vue";
    import LogUtils from "../../utils/logs";

    export default {
        components: {
            LogLine,
            TaskRunDetails,
            LogLevelSelector,
            LogLevelNavigator,
            Kicon,
            Download,
            Magnify,
            Collapse,
            Restart,
            DynamicScroller,
            DynamicScrollerItem,
        },
        data() {
            return {
                fullscreen: false,
                level: undefined,
                filter: undefined,
                openedTaskrunsCount: 0,
                raw_view: false,
                logIndicesByLevel: Object.fromEntries(LogUtils.levelOrLower(undefined).map(level => [level, []])),
                logCursor: undefined,
                temporalCursor: undefined,
                logLineRefs: {},
                filterUpdateCount: 0
            };
        },
        created() {
            this.level = (this.$route.query.level || localStorage.getItem("defaultLogLevel") || "INFO");
            this.filter = (this.$route.query.q || undefined);
        },
        watch:{
            level: {
                handler() {
                    if (this.raw_view) {
                        this.fetchLogs();
                    }
                },
                immediate: true
            },
            temporalCursor(newValue) {
                if (newValue !== undefined && this.raw_view) {
                    this.scrollToLog(newValue);
                }
            },
        },
        computed: {
            State() {
                return State
            },
            temporalLogs() {
                if (!this.logs?.length) return [];

                const filtered = this.logs.filter(log => {
                    if (!this.filter) return true;
                    return log.message?.toLowerCase().includes(this.filter.toLowerCase());
                });

                return filtered.map((logLine, index) => ({
                    ...logLine,
                    index,
                    uniqueId: `${logLine.timestamp || ""}-${logLine.taskId || ""}-${index}`
                }));
            },
            temporalLogIndicesByLevel()
            {
                const groupedLogIndices = this.temporalLogs.reduce((acc, item) => {
                    if (!acc[item.level]) {
                        acc[item.level] = [];
                    }
                    acc[item.level].push(item.index.toString());
                    return acc;
                }, {});
                const defaultLevels = Object.fromEntries(LogUtils.levelOrLower(undefined).map(level => [level, []]));
                return Object.entries(defaultLevels).reduce((acc, [level, array]) => {
                    acc[level] = groupedLogIndices[level] || array;
                    return acc;
                }, {});
            },
            ...mapState("execution", ["execution", "logs", "flow"]),
            downloadName() {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.execution.id}.log`
            },
            logDisplayButtonText() {
                return this.openedTaskrunsCount === 0 ? this.$t("expand all") : this.$t("collapse all")
            },
            currentLevelOrLower() {
                return LogUtils.levelOrLower(this.level);
            },
            countByLogLevel() {
                return !this.raw_view ? Object.fromEntries(Object.entries(this.logIndicesByLevel).map(([level, indices]) => [level, indices.length])):
                    Object.fromEntries(Object.entries(this.temporalLogIndicesByLevel).map(([level, indices]) => [level, indices.length]));
            },
            cursorLogLevel() {
                return !this.raw_view? Object.entries(this.logIndicesByLevel).find(([_, indices]) => indices.includes(this.logCursor))?.[0]:
                    Object.entries(this.temporalLogIndicesByLevel).find(([_, indices]) => indices.includes(this.temporalCursor))?.[0];
            },
            cursorIdxForLevel() {
                return !this.raw_view ? this.logIndicesByLevel?.[this.cursorLogLevel]?.toSorted(this.sortLogsByViewOrder)?.indexOf(this.logCursor):
                    this.temporalLogIndicesByLevel?.[this.cursorLogLevel]?.toSorted(this.sortLogsByViewOrder)?.indexOf(this.temporalCursor)
            }
        },
        methods: {
            shouldShowLog(log) {
                if (!this.filter) return true
                return log.message?.toLowerCase().includes(this.filter.toLowerCase())
            },
            setLogLineRef(index, el) {
                if (el) {
                    this.logLineRefs[index] = el;
                } else {
                    delete this.logLineRefs[index];
                }
            },
            downloadContent() {
                this.$store.dispatch("execution/downloadLogs", {
                    executionId: this.execution.id,
                    params: {
                        minLevel: this.level
                    }
                }).then((response) => {
                    Utils.downloadUrl(window.URL.createObjectURL(new Blob([response])), this.downloadName);
                });
            },
            forwardEvent(type, event) {
                this.$emit(type, event);
            },
            prevent(event) {
                event.preventDefault();
            },
            onChange() {
                this.$router.push({query: {...this.$route.query, q: this.filter, level: this.level, page: 1}});
                if(this.raw_view) this.filterUpdateCount++
            },
            expandCollapseAll() {
                this.$refs.logs.toggleExpandCollapseAll();
            },
            setRawView() {
                this.raw_view = !this.raw_view;
                if(this.raw_view && this.temporalCursor!==undefined)      
                {
                    setTimeout(() => {
                        this.scrollToLog(this.temporalCursor),300
                    });
                }
                else if(!this.raw_view && this.logCursor !== undefined)
                {
                    setTimeout(() => {
                        this.$refs.logs.scrollToLog(this.logCursor),300
                    });
                }
            },
            fetchLogs(executionId = this.execution.id)
            {   
                this.$store.dispatch("execution/loadLogs", {
                    executionId: executionId,
                    params: {
                        minLevel: this.level
                    },
                })
            },
            sortLogsByViewOrder(a, b) {
                const aSplit = a.split("/");
                const taskRunIndexA = aSplit?.[0];
                const bSplit = b.split("/");
                const taskRunIndexB = bSplit?.[0];
                if (taskRunIndexA === undefined) {
                    return taskRunIndexB === undefined ? 0 : -1;
                }
                if (taskRunIndexB === undefined) {
                    return 1;
                }
                if (taskRunIndexA === taskRunIndexB) {
                    return this.sortLogsByViewOrder(aSplit.slice(1).join("/"), bSplit.slice(1).join("/"));
                }

                return Number.parseInt(taskRunIndexA) - Number.parseInt(taskRunIndexB);
            },
            previousLogForLevel(level) { 
                const temporalIndex = this.temporalLogIndicesByLevel[level];
                const logIndicesForLevel = this.logIndicesByLevel[level];
                if(this.temporalCursor  === undefined || level !== this.cursorLogLevel || this.logCursor === undefined  ){
                    this.temporalCursor = temporalIndex?.[temporalIndex.length - 1];
                    this.logCursor = logIndicesForLevel?.[logIndicesForLevel.length - 1];
                }
                else{
                    const sorted = [...temporalIndex, this.temporalCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                    this.temporalCursor = sorted?.[sorted.indexOf(this.temporalCursor) - 1] ?? sorted[sorted.length - 1];
                    const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                    this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) - 1] ?? sortedIndices[sortedIndices.length - 1];
                }
            },
            nextLogForLevel(level) { 
                const temporalIndex = this.temporalLogIndicesByLevel[level];
                const logIndicesForLevel = this.logIndicesByLevel[level]; // ["1/0"]
                if(this.temporalCursor  === undefined || level !== this.cursorLogLevel || this.logCursor === undefined ){ 
                    this.temporalCursor = temporalIndex?.[0];
                    this.logCursor = logIndicesForLevel?.[0];
                }
                else{
                    const sorted = [...temporalIndex, this.temporalCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                    this.temporalCursor = sorted?.[sorted.indexOf(this.temporalCursor) + 1] ?? sorted[0];
                    const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                    this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) + 1] ?? sortedIndices[0];
                }  
            },
            clearLogLevel(level) {
                if ((this.logCursor !== undefined || this.temporalCursor!== undefined) && this.cursorLogLevel === level) {
                    this.logCursor = undefined;
                    this.temporalCursor = undefined
                }
            },
            getTemporalLogsSize()
            {
                return this.logs ? this.logs.length : 50;
            },
            scrollToLog(index) {
                this.$nextTick(() => {
                    if (!index || !this.$refs.logScroller) return;
                    const parsedIndex = parseInt(index);
                    const scroller = this.$refs.logScroller;
                    scroller.scrollToItem(parsedIndex, {
                    });
                    setTimeout(() => {
                        if (this.logLineRefs[parsedIndex]?.$el) {
                            this.logLineRefs[parsedIndex].$el.scrollIntoView({
                                behavior: "smooth",
                                block: "start"
                            });
                        }
                    }, 50);
                })
            }
        }
    };
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";
    .attempt-wrapper {
        background-color: var(--bs-white);

        :deep(.vue-recycle-scroller__item-view + .vue-recycle-scroller__item-view) {
            border-top: 1px solid var(--bs-border-color);
        }

        html.dark & {
            background-color: var(--bs-gray-100);
        }

        .attempt-wrapper & {
            border-radius: .25rem;
        }
        }
    .log-lines {
        max-height: calc(100vh - 335px);
        transition: max-height 0.2s ease-out;
        margin-top: calc(var(--spacer) / 2);

        .line {
            padding: calc(var(--spacer) / 2);

            &.cursor {
                background-color: var(--bs-gray-300)
            }
        }

        &::-webkit-scrollbar {
            width: 5px;
        }

        &::-webkit-scrollbar-track {
            background: var(--bs-gray-500);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--bs-primary);
        }
        }
</style>