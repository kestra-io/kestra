<template>
    <el-card class="d-flex flex-column gap-3">
        <template #header>
            <el-text>{{ name }}</el-text>
            <el-text class="d-block fw-normal" v-if="price">
                {{ price }}
            </el-text>
        </template>
        <ul>
            <li
                v-for="feature in features"
                :key="feature"
            >
                <Check class="me-2 text-success" /><el-text>{{ feature }}</el-text>
            </li>
        </ul>

        <a v-if="button?.href" class="mt-auto" :href="button.href">
            <el-button type="primary" class="w-100">{{ button.text }}</el-button>
        </a>
        <el-button v-else-if="button" class="mt-auto w-100" disabled>
            {{ button.text }}
        </el-button>
    </el-card>
</template>
<script setup>
    import Check from "vue-material-design-icons/Check.vue"
</script>
<script>
    export default {
        props: {
            name: {
                type: String,
                required: true
            },
            price: {
                type: String,
                default: undefined
            },
            features: {
                type: Array,
                default: () => []
            },
            /**
             * {
             *     (disabled: false),
             *     text: "String"
             * }
             */
            button: {
                type: Object,
                default: undefined
            }
        }
    };
</script>
<style scoped>
    .el-card {
        padding: 1rem 2rem;

        &:deep(.el-card__header) {
            border-bottom: 0;

            .el-text:first-child {
                font-size: 1.125rem;
            }
        }

        & >:deep(*) {
            padding: 0;
        }

        &:deep(.el-card__body) {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 1rem;

            ul {
                display: flex;
                flex-direction: column;
                list-style: none;
                padding: 0;
                margin: 0;
                gap: .5rem;
            }
        }
    }
</style>