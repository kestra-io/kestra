<template>
    <div class="bulk-action-button-div">
        <kicon :tooltip="$t('restart')">
            <el-button type="success" class="bulk-button" @click="this.restart()">
                <restart/>
            </el-button>
        </kicon>
        <kicon :tooltip="$t('kill')">
            <el-button type="warning" class="bulk-button" @click="this.kill()" :tooltip="$t('kill')">
                <stop-circle-outline/>
            </el-button>
        </kicon>
        <kicon :tooltip="$t('delete')">
            <el-button type="danger" class="bulk-button" @click="this.delete()" :tooltip="$t('delete')">
                <delete/>
            </el-button>
        </kicon>
        <el-dialog
            :title="$t(action)"
            v-model="isOpen"
        >
            <span v-if="action == 'restart'">
                {{ $t("bulk restart", {"executionCount": executionCount}) }}
            </span>
            <span v-else-if="action == 'kill'">
                {{ $t("bulk kill", {"executionCount": executionCount}) }}
            </span>
            <span v-else>
                {{ $t("bulk delete", {"executionCount": executionCount}) }}
            </span>
            <div slot="footer" class="dialog-footer">
                <el-button @click="isOpen = false">{{ $t('cancel') }}</el-button>
                <el-button type="primary" @click="valid">{{ $t('confirmation') }}</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import Restart from "vue-material-design-icons/Restart.vue";
import Delete from "vue-material-design-icons/Delete.vue";
import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
import Kicon from "../Kicon.vue"

export default {
    name: "BulkActionButton",
    components: {Restart, Delete, StopCircleOutline, Kicon},
    emits: ["restart", "kill", "delete"],
    data() {
        return {
            action: "",
            isOpen: false
        }
    },
    props: {
        executionCount: {
            type: Number,
        },
    },
    methods: {
        restart() {
            this.isOpen = true;
            this.action = 'restart'
        },
        kill() {
            this.isOpen = true;
            this.action = 'kill'
        },
        delete() {
            this.isOpen = true;
            this.action = 'delete'
        },
        valid() {
            this.isOpen = false
            this.$emit(this.action)
        }
    }
}
</script>

<style scoped>
.bulk-action-button-div {
    display: flex;
    justify-content: space-between;
    width: 160px;
}

.dialog-footer {
    padding: 10px 20px 20px;
    text-align: right;
    box-sizing: border-box;
}
</style>