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
                    <restart :flow="flow" :execution="execution" class="ms-0" @follow="forwardEvent('follow', $event)" />
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
            :level="level"
            :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']"
            :filter="filter"
            :level-to-highlight="cursorLogLevel"
            @log-cursor="logCursor = $event"
            :log-cursor="logCursor"
            @follow="forwardEvent('follow', $event)"
            @opened-taskruns-count="openedTaskrunsCount = $event"
            @log-indices-by-level="Object.entries($event).forEach(([levelName, indices]) => logIndicesByLevel[levelName] = indices)"
            :target-execution="execution"
            :target-flow="flow"
            :show-progress-bar="false"
        />
        <el-card v-else>
            <template v-for="log in logs" :key="`${log.timestamp}-${log.taskRun}`">
                <log-line
                    :level="level"
                    filter=""
                    :log="log"
                    title
                />
            </template>
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
            Restart
        },
        data() {
            return {
                fullscreen: false,
                level: undefined,
                filter: undefined,
                openedTaskrunsCount: 0,
                raw_view: false,
                logIndicesByLevel: Object.fromEntries(LogUtils.levelOrLower(undefined).map(level => [level, []])),
                logCursor: undefined
            };
        },
        created() {
            this.level = (this.$route.query.level || localStorage.getItem("defaultLogLevel") || "INFO");
            this.filter = (this.$route.query.q || undefined);
        },
        computed: {
            State() {
                return State
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
                return Object.fromEntries(Object.entries(this.logIndicesByLevel).map(([level, indices]) => [level, indices.length]));
            },
            cursorLogLevel() {
                return Object.entries(this.logIndicesByLevel).find(([_, indices]) => indices.includes(this.logCursor))?.[0];
            },
            cursorIdxForLevel() {
                return this.logIndicesByLevel?.[this.cursorLogLevel]?.toSorted(this.sortLogsByViewOrder)?.indexOf(this.logCursor);
            }
        },
        methods: {
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
            },
            expandCollapseAll() {
                this.$refs.logs.toggleExpandCollapseAll();
            },
            setRawView() {
                this.raw_view = !this.raw_view;
                if(this.raw_view) {
                    this.$store.dispatch("execution/loadLogs", {
                        executionId: this.execution.id,
                        minLevel: this.level
                    })
                }
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
                const logIndicesForLevel = this.logIndicesByLevel[level];
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[logIndicesForLevel.length - 1];
                    return;
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) - 1] ?? sortedIndices[sortedIndices.length - 1];
            },
            nextLogForLevel(level) {
                const logIndicesForLevel = this.logIndicesByLevel[level];
                if (this.logCursor === undefined) {
                    this.logCursor = logIndicesForLevel?.[0];
                    return;
                }

                const sortedIndices = [...logIndicesForLevel, this.logCursor].filter(Utils.distinctFilter).sort(this.sortLogsByViewOrder);
                this.logCursor = sortedIndices?.[sortedIndices.indexOf(this.logCursor) + 1] ?? sortedIndices[0];
            },
            clearLogLevel(level) {
                if (this.logCursor !== undefined && this.cursorLogLevel === level) {
                    this.logCursor = undefined;
                }
                if (this.level === level) {
                    this.level = undefined;
                    this.onChange();
                }
            }   

        }
    };
</script>
