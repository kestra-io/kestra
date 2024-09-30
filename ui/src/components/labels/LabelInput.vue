<template>
    <div class="d-flex w-100 mb-2" v-for="(label, index) in locals" :key="index">
        <div class="flex-grow-1 d-flex align-items-center">
            <el-input
                class="form-control me-2"
                :placeholder="$t('key')"
                v-model="label.key"
                :disabled="localExisting.includes(label.key)"
                @update:model-value="update(index, $event, 'key')"
            />
            <el-input
                class="form-control me-2"
                :placeholder="$t('value')"
                v-model="label.value"
                @update:model-value="update(index, $event, 'value')"
            />
        </div>
        <div class="flex-shrink-1">
            <el-button-group class="d-flex">
                <el-button :icon="Plus" @click="addItem" />
                <el-button :icon="Minus" @click="removeItem(index)" :disabled="index === 0 && locals.length === 1" />
            </el-button-group>
        </div>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
</script>


<script>
    export default {
        props: {
            labels: {
                type: Array,
                required: true
            },
            existingLabels: {
                type: Array,
                default: () => []
            }
        },
        data() {
            return {
                locals: [],
                localExisting: []
            }
        },
        emits: ["update:labels"],
        created() {
            if (this.labels.length === 0) {
                this.addItem();
            } else {
                this.locals = this.labels
            }
            this.localExisting = this.existingLabels.map(label => label.key);
        },
        methods: {
            addItem() {
                this.locals.push({key: null, value: null});
                this.$emit("update:labels", this.locals);
            },
            removeItem(index) {
                this.locals.splice(index, 1);
                this.$emit("update:labels", this.locals);
            },
            update(index, value, prop) {
                this.locals[index][prop] = value;
                this.$emit("update:labels", this.locals);
            },
        },
    }
</script>