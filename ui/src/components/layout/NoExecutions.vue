vue
<template>
    <el-card>
        <div class="header-image-container">
            <img :src="noExecutionsInFlowImage" alt="No Executions">
        </div>
        <div class="container" :class="themeClass">
            <h1 class="title">
                {{ $t('no-executions-view.title') }}
            </h1>
            <p class="subtitle">
                {{ $t('no-executions-view.sub_title') }}
            </p>
            <el-button type="primary" size="small" text>
                {{ $t('no-executions-view.launch_execution') }}
            </el-button>
        </div>
    </el-card>
    <div class="guidance">
        <h2>{{ $t('no-executions-view.guidance_desc') }}</h2>
        <h2>{{ $t('no-executions-view.guidance_sub_desc') }}</h2>
        <div class="cards">
            <div class="card">
                <h3>{{ $t('no-executions-view.get_started_title') }}</h3>
                <p>{{ $t('no-executions-view.get_started_desc') }}</p>
                <a href="#" class="link">Learn more →</a>
            </div>
            <div class="card">
                <h3>{{ $t('no-executions-view.workflow_components_title') }}</h3>
                <p>{{ $t('no-executions-view.workflow_components_desc') }}</p>
                <a href="#" class="link">Learn more →</a>
            </div>
            <div class="card">
                <h3>{{ $t('no-executions-view.videos_tutorials_title') }}</h3>
                <p>{{ $t('no-executions-view.videos_tutorials_desc') }}</p>
                <a href="#" class="link">Watch →</a>
            </div>
        </div>
    </div>
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
  text-align: center;
  min-height: 100vh;
}
.title {
  font-size: var(--el-font-size);
  margin-bottom: 5px;
}
.subtitle {
  font-size: var(--el-font-size-extra-small);
}
.theme-light {
    color: #000;
}
.theme-dark {
    color: #fff;
}
.cards {
  display: flex;
  justify-content: center;
  gap: 20px;
  margin-top: 20px;
}
.card {
  border-radius: 8px;
  padding: 20px;
  width: 200px;
  transition: transform 0.2s;
}
.card:hover {
  transform: scale(1.05);
}
.link {
  color: #5c5cff;
  text-decoration: none;
}
.onboarding-glow {
  animation: glowAnimation 1s infinite alternate;
}
@keyframes glowAnimation {
  0% {
    box-shadow: 0px 0px 0px 0px #8405FF;
  }
  100% {
    box-shadow: 0px 0px 50px 2px #8405FF;
  }
}
</style>