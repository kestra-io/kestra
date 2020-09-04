<template>
    <b-row v-if="revisions">
        <b-col md="5">
            <b-form-select v-model="revisionLeft" :options="options"></b-form-select>
            <pre>{{revisionLeftText}}</pre>
        </b-col>
        <b-col md="2">
            <pre class="diff text-center text-info">{{diff}}</pre>
        </b-col>
        <b-col md="5">
            <b-form-select v-model="revisionRight" :options="options"></b-form-select>
            <pre>{{revisionRightText}}</pre>
        </b-col>
    </b-row>
</template>
<script>
import { mapState } from "vuex";
import Yaml from "yaml";

export default {
    created() {
        this.$store.dispatch("flow/loadRevisions", this.$route.params);
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
            revisionLeft: undefined,
            revisionRight: undefined,
        };
    },
};
</script>
<style lang="scss">
@import "../../styles/variable";
.diff {
    margin-top: 38px;
    background-color: $white;
    border: none;
}
</style>