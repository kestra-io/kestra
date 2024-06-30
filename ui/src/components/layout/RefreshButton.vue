<template>
    <el-button-group :data-component="dataComponent" :size="size" :class="customClass">
        <el-button :disabled="!canAutoRefresh" :active="autoRefresh" @click="toggleAutoRefresh">
            <kicon :tooltip="$t('toggle periodic refresh each 10 seconds')" placement="bottom">
                <component :is="autoRefresh ? 'auto-renew' : 'auto-renew-off'" class="auto-refresh-icon" />
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
    import AutoRenew from "vue-material-design-icons/Autorenew.vue";
    import AutoRenewOff from "vue-material-design-icons/AutorenewOff.vue";
    import Kicon from "../Kicon.vue"
    import BaseComponents from "../BaseComponents.vue"


    export default {
        extends: BaseComponents,
        components: {Refresh, AutoRenew, AutoRenewOff, Kicon},
        emits: ["refresh"],
        props: {
            canAutoRefresh: {
                type: Boolean,
                default: true
            },
            size: {
                type: String,
                default: "default"
            },
            customClass: {
                type: String,
                default: ""
            }
        },
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
        },
        watch: {
            canAutoRefresh(newValue) {
                if (!newValue && this.autoRefresh) {
                    this.toggleAutoRefresh();
                    this.stopRefresh();
                }
            },
            autoRefresh(newValue) {
                if (newValue) {
                    this.refreshHandler = setInterval(this.triggerRefresh, 10000);
                    this.triggerRefresh()
                } else {
                    this.stopRefresh();
                }
            }
        }
    };
</script>
<style scoped lang="scss">
    button[active=true] .auto-refresh-icon {
        animation: rotate 10s linear infinite;
    }
    @keyframes rotate {
        0% {
            rotate: 0deg;
        }
        100% {
            rotate: 360deg;
        }
    }
</style>