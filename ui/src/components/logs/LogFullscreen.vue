<template>
    <div class="fullscreen-wrapper bg-light" v-if="fullscreen">
        <b-row>
            <b-col>
                <logs />
            </b-col>
        </b-row>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import Logs from "../executions/Logs";
    export default {
        components: {
            Logs
        },
        computed: {
            ...mapState("log", ["fullscreen"])
        },
        methods: {
            toggleLogFullscreen() {
                this.$store.commit("log/setFullscreen", !this.fullscreen);
            }
        }
    };
</script>
<style lang="scss" scoped>
.fullscreen-wrapper {
  position: fixed;
  z-index: 1050;
  overflow-y: auto;
  left: 0px;
  top: 0px;
  width: 100%;
  height: 100%;
  padding: 8px;
}
</style>