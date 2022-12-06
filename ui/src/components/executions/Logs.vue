<template>
    <div>
        <collapse>
            <el-form-item>
                <el-input
                    :model-value="filter"
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
                    :value="level"
                    @update:model-value="onChange"
                />
            </el-form-item>
            <el-form-item>
                <el-button-group>
                    <el-button @click="copy">
                        <kicon :tooltip="$t('copy logs')">
                            <content-copy />
                        </kicon>
                    </el-button>
                    <el-button :download="downloadName" :href="downloadContent">
                        <kicon :tooltip="$t('download logs')">
                            <download />
                        </kicon>
                    </el-button>
                </el-button-group>
            </el-form-item>
        </collapse>

        <log-list :level="level" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" :filter="filter" />
    </div>
</template>

<script>
    import LogList from "../logs/LogList";
    import {mapState} from "vuex";
    import Download from "vue-material-design-icons/Download.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Kicon from "../Kicon";
    import LogLevelSelector from "../logs/LogLevelSelector";
    import Collapse from "../layout/Collapse.vue";

    export default {
        components: {
            LogList,
            LogLevelSelector,
            Kicon,
            Download,
            ContentCopy,
            Magnify,
            Collapse
        },
        data() {
            return {
                fullscreen: false,
                level: undefined,
                filter: undefined
            };
        },
        created() {
            this.level = (this.$route.query.level || "INFO");
            this.filter = (this.$route.query.q || undefined);
        },
        computed: {
            ...mapState("execution", ["execution", "taskRun", "logs"]),
            downloadContent() {
                return "data:text/plain;base64," + Buffer.from(this.contentAsText, "utf8").toString("base64");
            },
            contentAsText() {
                return this.logs.map(l => `${l.timestamp} | ${l.level} | ${l.message}`).join("\n")
            },
            downloadName() {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.execution.id}.log`
            }
        },
        methods: {
            prevent(event) {
                event.preventDefault();
            },
            onChange() {
                this.$router.push({query: {...this.$route.query, q: this.filter, level: this.level, page: 1}});
            },
            copy () {
                navigator.clipboard.writeText(this.contentAsText);
                this.$toast().success(this.$t("copied"));
            },
        }
    };
</script>
