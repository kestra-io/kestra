<template>
    <component
        :is="component"
        :icon="QueueFirstInLastOut"
        @click="click"
        v-if="enabled"
        class="ms-0 me-1"
    >
        {{ $t('unqueue') }}
    </component>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('unqueue title', {id: execution.id})" />
        </template>
        <template #footer>
            <el-button :icon="QueueFirstInLastOut" type="primary" @click="unqueue()" native-type="submit">
                {{ $t('unqueue') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup>
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"

    export default {
        props: {
            execution: {
                type: Object,
                required: true
            },
            component: {
                type: String,
                default: "el-button"
            },
        },
        data() {
            return {
                isDrawerOpen: false,
            };
        },
        methods: {
            click() {
                this.$toast()
                    .confirm(this.$t("unqueue confirm", {id: this.execution.id}), () => {
                        return this.unqueue();
                    });
            },
            unqueue() {
                this.$store
                    .dispatch("execution/unqueue", {
                        id: this.execution.id
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("unqueue done"));
                    });
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("execution", ["flow"]),
            enabled() {
                if (!(this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.execution.namespace))) {
                    return false;
                }

                return State.isQueued(this.execution.state.current);
            }
        },
    };
</script>

<style lang="scss" scoped>
    button.el-button {
        cursor: pointer !important;
    }
</style>
