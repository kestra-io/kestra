<template>
    <div>
        <b-navbar toggleable="lg" class="nav-filter mb-2">
            <b-navbar-toggle target="nav-collapse" />
            <b-collapse id="nav-collapse" is-nav>
                <b-nav-form @submit.prevent="prevent">
                    <b-form-input
                        :label="$t('search')"
                        @input="onChange"
                        v-model="filter"
                        :placeholder="$t('search')"
                    />
                    <log-level-selector
                        v-model="level"
                        @input="onChange"
                    />
                    <b-button-group>
                        <b-button @click="copy">
                            <kicon :tooltip="$t('copy logs')">
                                <content-copy />
                            </kicon>
                        </b-button>
                        <b-button :download="downloadName" :href="downloadContent">
                            <kicon :tooltip="$t('download logs')">
                                <download />
                            </kicon>
                        </b-button>
                        <b-button @click="toggleLogFullscreen">
                            <kicon :tooltip="$t('toggle fullscreen')">
                                <full-screen-exit v-if="fullscreen" />
                                <full-screen v-else />
                            </kicon>
                        </b-button>
                    </b-button-group>
                </b-nav-form>
            </b-collapse>
        </b-navbar>

        <log-list :level="level" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" :filter="filter" />

        <b-modal
            hide-footer
            scrollable
            visible
            id="log-fullscreen-modal"
            modal-class="fullscreen"
            v-if="fullscreen"
            @close="toggleLogFullscreen"
        >
            <log-list v-if="fullscreen" :full-screen-modal="true" :level="level" :exclude-metas="['namespace', 'flowId', 'taskId', 'executionId']" />
        </b-modal>
    </div>
</template>
<script>
    import LogList from "../logs/LogList";
    import {mapState} from "vuex";
    import FullScreenExit from "vue-material-design-icons/FullscreenExit.vue";
    import FullScreen from "vue-material-design-icons/Fullscreen.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Kicon from "../Kicon";
    import LogLevelSelector from "../logs/LogLevelSelector";

    export default {
        components: {
            LogList,
            LogLevelSelector,
            Kicon,
            FullScreenExit,
            FullScreen,
            Download,
            ContentCopy,
        },
        data() {
            return {
                fullscreen: false,
                level: (this.$route.query.level || "INFO"),
                filter: (this.$route.query.q || undefined)
            };
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
            toggleLogFullscreen() {
                this.fullscreen = !this.fullscreen;
            },
            copy () {
                navigator.clipboard.writeText(this.contentAsText);
                this.$toast().success(this.$t("copied"));
            },
        }
    };
</script>
