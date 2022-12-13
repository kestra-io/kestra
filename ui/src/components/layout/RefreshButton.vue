<template>
    <el-button-group>
        <el-button @click="toggleAutoRefresh" :pressed="autoRefresh">
            <kicon :tooltip="$t('toggle periodic refresh each 10 seconds')" placement="bottom">
                <clock />
            </kicon>
        </el-button>
        <el-button @click="triggerRefresh">
            <kicon :tooltip="$t('trigger refresh')" placement="bottom">
                <refresh />
            </kicon>
        </el-button>
    </el-button-group>
</template>
<script>
    import Refresh from "vue-material-design-icons/Refresh.vue";
    import Clock from "vue-material-design-icons/Clock.vue";
    import Kicon from "../Kicon.vue"
    export default {
        components: {Refresh, Clock, Kicon},
        emits: ["refresh"],
        data() {
            return {
                autoRefresh: false,
                refreshHandler: undefined
            };
        },
        created() {
            this.autoRefresh = localStorage.getItem("autoRefresh") === "1";
        },
        methods: {
            toggleAutoRefresh() {
                this.autoRefresh = !this.autoRefresh;
                localStorage.setItem("autoRefresh", this.autoRefresh ? "1" : "0");
                if (this.autoRefresh) {
                    this.refreshHandler = setInterval(this.triggerRefresh, 10000);
                    this.triggerRefresh()
                } else {
                    this.stopRefresh();
                }
            },
            triggerRefresh() {
                this.$emit("refresh");
            },
            stopRefresh() {
                if (this.refreshHandler) {
                    clearInterval(this.refreshHandler);
                    this.refreshHandler = undefined
                }
            }
        },
        beforeUnmount() {
            this.stopRefresh();
        }
    };
</script>

