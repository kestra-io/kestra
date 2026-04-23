import {defineConfig} from "tsdown"

export default defineConfig({
  platform: "browser",
  exports: "ci-only",
  fromVite: true,
  dts: {vue: true},
  copy: [
    {from: "src/assets/images", to: "dist/assets"},
  ],
  deps:{
    neverBundle: [/\.png$/, "@vue/reactivity"]
  } 
})
