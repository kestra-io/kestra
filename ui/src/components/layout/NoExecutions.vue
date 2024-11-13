<template>
    <el-card>
        <el-container class="header-image-container">
            <el-image :src="noExecutionsInFlowImage" alt="No Executions" fit="contain" />
        </el-container>
        <el-container :class="['container', themeClass]">
            <el-main>
                <el-title level="1" style="font-weight: 900;">
                    {{ $t('no-executions-view.title') }}
                </el-title>
                <el-title level="1">
                    {{ $t('no-executions-view.Kestra') }}
                </el-title>
                <el-row>
                    <el-col :span="24">
                        <el-title level="2">
                            {{ $t('no-executions-view.sub_title') }}
                        </el-title>
                    </el-col>
                    <el-col :span="24" style="margin-top: 30px;">
                        <router-link :to="{name: 'flows/list'}">
                            <el-button size="large" type="primary">
                                {{ $t('execute') }}
                            </el-button>
                        </router-link>
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
                                <el-link href="https://kestra.io/docs/installation" target="__blank" type="primary">
                                    Learn more →
                                </el-link>
                            </el-title>
                        </div>
                    </el-card>
                </el-col>

                <el-col :span="8" class="card__wrap--inner">
                    <el-card shadow="hover" class="card" style="width: 12vw;">
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
                            <el-link href="https://kestra.io/docs/tutorial" target="__blank" type="primary" style="padding-top: 53px;">
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
    export default {
        name: "NoExecutions",
        computed: {
            noExecutionsInFlowImage() {
                return noExecutionsImage
            },
            themeClass() {
                return (localStorage.getItem("theme") || "light") === "light"
                    ? "theme-light"
                    : "theme-dark";
            },
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
.theme-light {
    color: #000;
}
.theme-dark {
    color: #fff;
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
</style>