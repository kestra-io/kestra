<script>
    import Alert from "vue-material-design-icons/Alert.vue"
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"

    export default {
        name: "Message",
        components: {
            Alert,
            InformationOutline
        },
        props: {
            type: {
                type: String,
                default: "alert",
                validator(value) {
                    return ["alert", "info"].includes(value)
                }
            },
            text: {
                type: String,
                default: ""
            }
        },
    }
</script>

<template>
    <el-card class="my-2" body-class="p-2">
        <div class="d-flex justify-content-between align-items-center">
            <span class="text d-flex align-items-center">
                <InformationOutline v-if="this.type === 'info'" class="icon icon-info mx-3" />
                <Alert v-else class="icon icon-warning mx-3" />
                {{ this.text }}
            </span>
        </div>
    </el-card>
</template>

<style scoped lang="scss">
    .icon {
        font-size: 2rem;
    }
    .icon-info {
        color: var(--bs-info);
    }
    .icon-warning {
        color: var(--bs-warning);
    }
    .text {
        .html.dark & {
            color: white;
        }
    }
</style>