<template>
    <el-card class="overall-container">
        <el-container class="header-image-container">
            <el-image :src="noExecutionsInFlowImage" alt="No Executions" fit="contain" />
        </el-container>
        <el-container :class="['container', themeClass]">
            <el-main>
                <el-title level="1" style="font-weight: 900; font-size: 24px">
                    {{ $t('no-executions-view.title') }}
                </el-title>
                <el-row>
                    <el-col :span="24">
                        <el-title level="2">
                            {{ $t('no-executions-view.sub_title') }}
                        </el-title>
                    </el-col>
                    <el-col :span="24" style="margin-top: 30px;">
                        <el-button id="execute-button" :icon="icon.Flash" :type="primary" @click="onClick()" class="execute">
                            {{ $t("execute") }}
                        </el-button>
                    </el-col>
                    <el-col :span="24" style="margin-top: 30px; font-weight: 900;">
                        <el-title level="2">
                            {{ $t('no-executions-view.guidance_desc') }}
                        </el-title>
                    </el-col>
                    <el-col :span="24">
                        <el-title level="2">
                            {{ $t('no-executions-view.guidance_sub_desc') }}
                        </el-title>
                    </el-col>
                </el-row>
            </el-main>
        </el-container>
    
        <el-container :class="['container', themeClass]">
            <el-row class="card__wrap--outer" type="flex" justify="start" gutter="20">
                <el-col :span="8" class="card__wrap--inner">
                    <el-card shadow="hover" class="card">
                        <div class="card__item">
                            <el-title level="3">
                                {{ $t('no-executions-view.get_started_title') }}
                            </el-title>
                        </div>
                        <div class="card__sub">
                            <el-title level="3">
                                {{ $t('no-executions-view.get_started_desc') }}
                            </el-title>
                        </div>
                        <div class="card__footer">
                            <el-title level="3">
                                <el-link href="https://kestra.io/docs/installation" target="__blank" type="primary" style="padding-top: 35px;">
                                    Learn more →
                                </el-link>
                            </el-title>
                        </div>
                    </el-card>
                </el-col>

                <el-col :span="8" class="card__wrap--inner">
                    <el-card shadow="hover" class="card">
                        <div class="card__item">
                            <el-title level="3" style="margin-bottom: 10px;">
                                {{ $t('no-executions-view.workflow_components_title') }}
                            </el-title>
                        </div>
                        <div class="card__sub">
                            <el-title level="3">
                                {{ $t('no-executions-view.workflow_components_desc') }}
                            </el-title>
                        </div>
                      
                        <div class="card__footer">
                            <el-link href="https://kestra.io/docs/getting-started/workflow-components" target="__blank" type="primary" style="padding-top: 35px;">
                                Learn more →
                            </el-link>
                        </div>
                    </el-card>
                </el-col>

                <el-col :span="8" class="card__wrap--inner">
                    <el-card shadow="hover" class="card">
                        <div class="card__item">
                            <el-title level="3" style="margin-bottom: 10px;">
                                {{ $t('no-executions-view.videos_tutorials_title') }}
                            </el-title>
                        </div>
                        <div class="card__sub">
                            <el-title level="3" style="margin-bottom: 10px;">
                                {{ $t('no-executions-view.videos_tutorials_desc') }}
                            </el-title>
                        </div>
                        <div class="card__footer">
                            <el-link href="https://kestra.io/docs/tutorial" target="__blank" type="primary" style="padding-top: 70px;">
                                Watch →
                            </el-link>
                        </div>
                    </el-card>
                </el-col>
            </el-row>
        </el-container>
    </el-card>
</template>


<script>
    import noExecutionsImage from "../../assets/onboarding/onboarding-ready-to-flow.svg"
    import Flash from "vue-material-design-icons/Flash.vue";
    import {shallowRef} from "vue";
    export default {
        name: "NoExecutions",
        data() {
            return {
                icon: {
                    Flash: shallowRef(Flash)
                }
            };
        },
        computed: {
            noExecutionsInFlowImage() {
                return noExecutionsImage
            },
            themeClass() {
                return (localStorage.getItem("theme") || "light") === "light"
                    ? "theme-light"
                    : "theme-dark";
            },
        },
        methods: {
            triggerExecute() {
                this.$store.dispatch("executeFlow");
            }
        }
    };
</script>

<style scoped>
.header-image-container {
    display: flex;
    justify-content: center;
    align-items: center;
}
.container {
  margin-top:20px;
  text-align: center;
  font-size: var(--el-font-size-small);
  justify-content: center;
}
:root {
  --theme-background: #fff; 
}
.theme-light {
  --theme-background: #000; 
}
.theme-dark {
  --theme-background: #fff; 
}
.overall-container {
  position: relative;
  padding-top: 35px;
}
.overall-container::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 70%;
  height: 25%;
  background: linear-gradient(to bottom, 
    rgba(122, 0, 255, 0.5) 0%, 
    rgba(122, 0, 255, 0.5) 40%, 
    rgba(0, 0, 255, 0) 80%,
    rgba(0, 0, 255, 0) 100%
  );
  pointer-events: none;
}
.card {
  flex-grow: 1;
  display: flex; 
  flex-direction: column;
  border-radius: 8px;
  transition: transform 0.2s;
  height: 30vh;
  width: 10vw;
  position: sticky;
  top: 5vh;
  left: 10vw;
}
.card:hover {
  transform: scale(1.05);
}

.card__wrap--outer {
  display: flex;
  flex-wrap: wrap;
  text-align: left;
  font-size: var(--el-font-size-extra-small);
}

.card__wrap--inner {
  margin-bottom: 20px;
}

.card__item {
  margin-bottom: 10px;
}

.card__footer {
  overflow: hidden;
  padding-top: 10px;
}
.execute {
    background-color: var(--el-text-color-primary);
    padding: 20px 60px 20px 60px;
}
</style>