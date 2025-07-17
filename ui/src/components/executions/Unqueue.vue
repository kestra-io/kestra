<template>
    <component
        :is="component"
        :icon="QueueFirstInLastOut"
        @click="isDrawerOpen = !isDrawerOpen"
        v-if="enabled"
        class="ms-0 me-1"
    >
        {{ $t('unqueue') }}
    </component>

    <el-dialog v-if="isDrawerOpen" v-model="isDrawerOpen" destroy-on-close :append-to-body="true">
        <template #header>
            <span v-html="$t('unqueue')" />
        </template>

        <template #default>
            <p v-html="$t('unqueue title', {id: execution.id})" />

            <el-select
                :required="true"
                v-model="selectedStatus"
                :persistent="false"
            >
                <el-option
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                    :disabled="item.disabled"
                >
                    <template #default>
                        <status size="small" :label="true" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
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
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"
    import Status from "../../components/Status.vue";

    export default {
        components: {Status},
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
                selectedStatus: State.RUNNING,
            };
        },
        methods: {
            unqueue() {
                this.executionsStore
                    .unqueue({
                        id: this.execution.id,
                        state: this.selectedStatus
                    })
                    .then(() => {
                        this.isDrawerOpen = false;
                        this.$toast().success(this.$t("unqueue done"));
                    });
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(useExecutionsStore),
            states() {
                return [State.RUNNING, State.CANCELLED, State.FAILED].map(value => ({
                    code: value,
                    label: this.$t("unqueue as", {status: value}),
                }));
            },
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
