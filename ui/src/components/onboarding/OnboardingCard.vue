<template>
    <el-card @click="openLink">
        <Export class="icon" />
        <div class="content row">
            <div class="col-lg-4 col-xl-6">
                <img :src="imgSrc" class="img-fluid" alt="" />
            </div>
            <div class="col-lg-8 col-xl-6">
                <h3>{{ title }}</h3>
                <p>{{ content }}</p>
            </div>

        </div>

    </el-card>
</template>

<script>
    import Export from "vue-material-design-icons/Export.vue";
    import {ElMessageBox} from 'element-plus'

    export default {
        name: "OnboardingCard",
        components: {
            Export,
        },
        props: {
            title: {
                type: String,
                required: true
            },
            content: {
                type: String,
                required: true
            },
            imgClass: {
                type: String,
                required: true
            },
            link: {
                type: String,
                required: true
            }
        },
        methods: {
            openLink() {
                if (this.link.indexOf("<") >= 0) {
                    ElMessageBox.alert(
                        this.link,
                        this.title,
                        {
                            customClass: "full-screen",
                            confirmButtonText: this.$t("close"),
                            dangerouslyUseHTMLString: true,
                        }
                    )
                } else {
                    window.open(this.link, "_blank");
                }
            }
        },
        computed: {
            imgSrc() {
                const darkTheme = document.getElementsByTagName("html")[0].className.indexOf("dark") >= 0;

                return new URL("../../assets/onboarding/onboarding-" + this.imgClass + "-" + (darkTheme ? "dark" : "light") + ".svg", import.meta.url).href;
            },
        }
    }
</script>

<style scoped lang="scss">
    .el-card {
        padding: var(--spacer);
        position: relative;
        height: 100%;
        cursor: pointer;
    }

    p {
        margin-bottom: 0;
    }

    .icon {
        position: absolute;
        top: calc(var(--spacer) / 2);
        right: calc(var(--spacer) / 2);
        font-size: var(--font-size-lg);
    }

    p, .icon {
        color: var(--bs-gray-500);

        html.dark & {
            color: var(--bs-gray-700);
        }
    }

    img {
        max-width: 100%;
    }
</style>