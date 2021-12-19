<template>
    <div>
        <b-row>
            <b-col>
                <div class="float-right">
                    <b-button @click="copy">
                        <kicon :tooltip="$t('copy logs')">
                            <content-copy />
                        </kicon>
                    </b-button>
                    <a :download="downloadName" :href="downloadContent">
                        <b-button class="ml-2">
                            <kicon :tooltip="$t('download logs')">
                                <download />
                            </kicon>
                        </b-button>
                    </a>
                    <b-button class="ml-2" @click="toggleLogFullscreen">
                        <kicon :tooltip="$t('toggle fullscreen')">
                            <full-screen-exit v-if="fullscreen" />
                            <full-screen v-else />
                        </kicon>
                    </b-button>
                </div>
            </b-col>
        </b-row>

        <log-filters @input="onChange" :filter="filterTerm" :level="level" />
        <log-list :level="level" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" :filter="filterTerm" />
    </div>
</template>
<script>
    import LogList from "../logs/LogList";
    import LogFilters from "../logs/LogFilters";
    import {mapState} from "vuex";
    import FullScreenExit from "vue-material-design-icons/FullscreenExit.vue";
    import FullScreen from "vue-material-design-icons/Fullscreen.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Kicon from "../Kicon";
    
    export default {
        components: {
            LogList, 
            LogFilters, 
            Kicon, 
            FullScreenExit, 
            FullScreen,
            Download,
            ContentCopy,
        },
        computed: {
            ...mapState("execution", ["execution", "taskRun", "logs"]),
            ...mapState("log", ["fullscreen"]),
            filterTerm() {
                return (this.$route.query.q || "").toLowerCase();
            },
            level() {
                return this.$route.query.level || "INFO";
            },
            downloadContent() {
                return "data:text/plain;base64," + btoa(this.contentAsText)
            },
            contentAsText() {
                return this.logs.map(l => `${l.timestamp} | ${l.level} | ${l.message}`).join("\n")
            },
            downloadName() {
                return `kestra-execution-${this.$moment().format("YYYYMMDDHHmmss")}-${this.execution.id}.log`
            }
        },
        methods: {
            onChange(event) {
                this.$router.push({query: {...this.$route.query, q: event.filter, level: event.level, page: 1}});
            },
            toggleLogFullscreen() {
                this.$store.commit("log/setFullscreen", !this.fullscreen)
                if (this.fullscreen) {
                    this.$bvModal.show("log-fullscreen-modal")
                } else {
                    this.$bvModal.hide("log-fullscreen-modal")
                }
            },
            copy () {
                navigator.clipboard.writeText(this.contentAsText);
                this.$toast().success(this.$t("copied"));
            },
        }
    };
</script>
