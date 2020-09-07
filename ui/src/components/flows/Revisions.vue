<template>
    <div v-if="revisions && revisions.length > 1">
        <b-row>
            <b-col md="12">
                <b-form-select v-model="displayType" :options="displayTypes"></b-form-select>
                <hr />
            </b-col>

            <b-col md="6">
                <b-form-select v-model="revisionLeft" :options="options"></b-form-select>
            </b-col>
            <b-col md="6">
                <b-form-select v-model="revisionRight" :options="options"></b-form-select>
            </b-col>
            <b-col md="12">
                <br />
                <code-diff
                    :outputFormat="displayType"
                    :old-string="revisionLeftText"
                    :new-string="revisionRightText"
                    :context="10"
                />
            </b-col>
        </b-row>
    </div>
    <div v-else>
        <b-alert class="mb-0" show>{{$t('no revisions found')}}</b-alert>
    </div>
</template>
<script>
import { mapState } from "vuex";
import Yaml from "yaml";
import CodeDiff from "vue-code-diff";

export default {
    components: { CodeDiff },
    created() {
        this.$store
            .dispatch("flow/loadRevisions", this.$route.params)
            .then(() => {
                const revisionLength = this.revisions.length;
                if (revisionLength > 0) {
                    this.revisionRight = revisionLength - 1;
                }
                if (revisionLength > 1) {
                    this.revisionLeft = revisionLength - 2;
                }
                if (this.$route.query.revisionRight) {
                    this.revisionRight = this.revisionIndex(
                        this.$route.query.revisionRight
                    );
                    if (
                        !this.$route.query.revisionLeft &&
                        this.revisionRight > 0
                    ) {
                        this.revisionLeft = this.revisions.length - 1;
                    }
                }
                if (this.$route.query.revisionLeft) {
                    this.revisionLeft = this.revisionIndex(
                        this.$route.query.revisionLeft
                    );
                }
            });
    },
    methods: {
        revisionIndex(revision) {
            const rev = parseInt(revision);
            for (let i = 0; i < this.revisions.length; i++) {
                if (rev === this.revisions[i].revision) {
                    return i;
                }
            }
        },
    },
    computed: {
        ...mapState("flow", ["revisions"]),
        options() {
            return (this.revisions || []).map((revision, x) => {
                return {
                    value: x,
                    text: revision.revision,
                };
            });
        },
        revisionLeftText() {
            if (this.revisionLeft === undefined) {
                return "";
            }
            return Yaml.stringify(this.revisions[this.revisionLeft]);
        },
        revisionRightText() {
            if (this.revisionRight === undefined) {
                return "";
            }
            return Yaml.stringify(this.revisions[this.revisionRight]);
        },
        diff() {
            const linesLeft = this.revisionLeftText.split("\n");
            const linesRight = this.revisionRightText.split("\n");
            const minLength = Math.min(linesLeft.length, linesRight.length);
            const diff = [];
            for (let i = 0; i < minLength; i++) {
                diff.push(linesLeft[i] === linesRight[i] ? "" : "≠");
            }
            return diff.join("\n");
        },
    },
    data() {
        return {
            revisionLeft: 0,
            revisionRight: 0,
            displayType: "side-by-side",
            displayTypes: [
                { value: "side-by-side", text: "side-by-side" },
                { value: "line-by-line", text: "line-by-line" },
            ],
        };
    },
};
</script>
