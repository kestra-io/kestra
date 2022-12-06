<template>
    <el-collapse-item :name="schedule.id">
        <template #title>
            {{ schedule.id }} <el-tag class="ms-2" disable-transitions type="info">{{ schedule.cron }}</el-tag>
        </template>
        <el-form class="ks-horizontal">
            <el-form-item :label="$t('id')">
                <el-input v-model="item.id" />
            </el-form-item>

            <el-form-item>
                <template #label>
                    {{ $t('schedules.cron.expression') }}

                    <el-tooltip :target="'tooltip-' + index" placement="bottom" :persistent="false">
                        <template #content>
                            <div v-if="isValid">
                                <p class="font-weight-bold">
                                    3 Next occurences
                                </p>

                                <span v-if="occurences.length">
                                    <span v-for="(occurence, x) in occurences" :key="x">{{ $filters.date(occurence) }}<br></span>
                                </span>
                            </div>
                            <span v-else>
                                {{ $t("schedules.cron.invalid") }}
                            </span>
                        </template>

                        <help />
                    </el-tooltip>
                </template>

                <el-input required v-model="item.cron" />

                <!--
                <b-form-invalid-feedback>
                    Enter at least 3 letters
                </b-form-invalid-feedback>

                <b-form-text>{{ cronHumanReadable }}</b-form-text>

                -->
            </el-form-item>

            <el-form-item :label="$t('schedules.cron.backfilll')">
                <el-date-picker
                    v-model="backfillStart"
                    type="datetime"
                />
            </el-form-item>

            <el-form-item class="text-end" label="&nbsp;">
                <el-button type="danger" @click="remove">
                    <delete />
                    Delete
                </el-button>
            </el-form-item>
        </el-form>



        <!--
        <b-form-group label-cols-sm="3" label-cols-lg="2" :label="$t('id')" :label-for="'input-id-' + index">
            <b-form-input required :id="'input-id-' + index" v-model="item.id" />
        </b-form-group>

        <b-form-group
            label-cols-sm="3"
            label-cols-lg="2"
            :label-for="'input-cron-' + index"
            :state="isValid"
        >
            <template #label>
                {{ $t('schedules.cron.expression') }}
                <el-link class="text-body" :id="'tooltip-' + index">
                    <help />
                </el-link>

                <b-tooltip :target="'tooltip-' + index" placement="bottom">
                    <div v-if="isValid">
                        <p class="font-weight-bold">
                            3 Next occurences
                        </p>

                        <span v-if="occurences.length">
                            <span v-for="(occurence, x) in occurences" :key="x">{{ $filters.date(occurence) }}<br></span>
                        </span>
                    </div>
                    <span v-else>
                        {{ $t("schedules.cron.invalid") }}
                    </span>
                </b-tooltip>
            </template>

            <b-form-input required :id="'input-cron-' + index" v-model="item.cron" />

            <b-form-invalid-feedback>
                Enter at least 3 letters
            </b-form-invalid-feedback>

            <b-form-text>{{ cronHumanReadable }}</b-form-text>
        </b-form-group>


        <b-form-group
            label-cols-sm="3"
            label-cols-lg="2"
            :label="$t('schedules.cron.backfilll')"
            :label-for="'input-' + index"
        >
            <date-picker
                v-model="backfillStart"
                :required="false"
                type="datetime"
                :id="'input-' + index"
            />
        </b-form-group>

        <b-form-group class="mb-0 text-end">
            <el-btn type="danger" @click="remove">
                <delete />
                Delete
            </el-btn>
        </b-form-group>
        -->
    </el-collapse-item>
</template>
<script>
    const cronstrue = require("cronstrue/i18n");
    const cronParser = require("cron-parser");
    const cronValidator = require("cron-validator");
    import Delete from "vue-material-design-icons/Delete";
    import Help from "vue-material-design-icons/HelpBox";

    export default {
        components: {
            Delete,
            Help,
        },
        props: {
            schedule: {
                type: Object,
                required: true
            },
            index: {
                type: Number,
                required: true
            }
        },
        emits: ["remove", "set"],
        data() {
            return {
                item: {
                    id: undefined,
                    cron: undefined,
                    backfill: undefined
                },
            }
        },
        watch: {
            schedule: function (item) {
                this.item = item;
            },
            item: {
                handler(val) {
                    this.emit(val);
                },
                deep: true
            }
        },
        created() {
            this.item = this.schedule;
        },
        computed: {
            backfillStart: {
                get: function () {
                    return this.item.backfill && this.item.backfill.start !== undefined ?
                        this.$moment(this.item.backfill.start).toDate() :
                        undefined;
                },
                set: function (val) {
                    let current = this.item;

                    if (current.backfill === undefined) {
                        current.backfill = {};
                    }

                    if (val) {
                        current.backfill = {"start": this.$moment(val).format()};
                    } else {
                        delete current.backfill;
                    }

                    this.emit(current);
                }
            },
            occurences() {
                const occurences = [];
                if (!this.isValid) {
                    return occurences;
                }
                const interval = cronParser.parseExpression(this.item.cron);
                for (let i = 0; i < 3; i++) {
                    occurences.push(interval.next().toDate());
                }
                return occurences;
            },
            cronHumanReadable() {
                const locale = localStorage.getItem("lang") || "en";
                try {
                    return cronstrue.toString(this.item.cron, {locale});
                } catch {
                    return this.$t("schedules.cron.invalid");
                }
            },
            isValid() {
                return this.item.cron && cronValidator.isValidCron(this.item.cron);
            }
        },
        methods: {
            remove() {
                this.$emit("remove", this.index);
            },
            emit(item) {
                if (item.backfill && Object.keys(item.backfill).length === 0) {
                    delete item.backfill;
                }

                this.$emit("set", this.index, item);
            },
        }
    };
</script>
