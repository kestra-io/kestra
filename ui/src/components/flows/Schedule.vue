<template>
    <div>
        <b-list-group v-if="triggers.length > 0">
            <schedule-item
                @remove="remove"
                @set="set"
                :schedule="schedule"
                :index="x"
                v-for="(schedule, x) in triggers"
                :key="x"
            />
        </b-list-group>
        <b-alert show variant="light" v-else class="text-muted mb-0">
            {{ $t('no result') }}
        </b-alert>
        <bottom-line v-if="canSave">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <b-button
                        variant="primary"
                        @click="addSchedule"
                        v-if="canSave"
                    >
                        <plus />
                        {{ $t("add schedule") }}
                    </b-button>

                    <b-button @click="save" v-if="canSave">
                        <content-save />
                        <span>{{ $t("save") }}</span>
                    </b-button>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>
<script>
    import {mapState} from "vuex";
    import ContentSave from "vue-material-design-icons/ContentSave";
    import Plus from "vue-material-design-icons/Plus";
    import ScheduleItem from "./ScheduleItem";
    import BottomLine from "../layout/BottomLine";
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
