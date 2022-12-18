<template>
    <div>
        <el-collapse v-if="triggers.length > 0" v-model="collapseActive">
            <schedule-item
                @remove="remove"
                @set="set"
                :schedule="schedule"
                :index="x"
                v-for="(schedule, x) in triggers"
                :key="x"
            />
        </el-collapse>
        <el-alert type="info" v-else :closable="false" class="mb-0">
            {{ $t('no result') }}
        </el-alert>
        <bottom-line v-if="canSave">
            <ul>
                <li>
                    <el-button
                        @click="addSchedule"
                        v-if="canSave"
                    >
                        <plus />
                        {{ $t("add schedule") }}
                    </el-button>

                    <el-button @click="save" v-if="canSave" type="primary">
                        <content-save />
                        <span>{{ $t("save") }}</span>
                    </el-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import ScheduleItem from "./ScheduleItem.vue";
    import BottomLine from "../layout/BottomLine.vue";
    import {
        canSaveFlowTemplate,
        saveFlowTemplate,
    } from "../../utils/flowTemplate";

    export default {
        components: {
            Plus,
            ContentSave,
            ScheduleItem,
            BottomLine,
        },
        data() {
            return {
                collapseActive: 0
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            canSave() {
                return canSaveFlowTemplate(true, this.user, this.flow, "flow");
            },
            triggers() {
                return (this.flow.triggers || []).filter(
                    (r) => r.type.endsWith("core.models.triggers.types.Schedule")
                )
            }
        },
        methods: {
            save() {
                saveFlowTemplate(this, this.flow, "flow");
            },
            set(index, schedule) {
                this.$store.commit("flow/setTrigger", {index, trigger: schedule});
            },
            remove(index) {
                this.$store.commit("flow/removeTrigger", index);
            },
            addSchedule() {
                this.$store.commit("flow/addTrigger", {
                    id: "schedule",
                    cron: "0 4 * * 1,4",
                    type: "io.kestra.core.models.triggers.types.Schedule",
                });
            },
        },
    };
</script>
<style lang="scss" scoped>
</style>
