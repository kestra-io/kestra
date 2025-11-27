import { federation } from "@module-federation/vite";
import vue from "@vitejs/plugin-vue";
import vueJsx from "@vitejs/plugin-vue-jsx";
import path from "path";
import { defineConfig } from "vite";

export default defineConfig(async ({ mode }) => {
  return {
    server: {
      fs: {
        allow: [".", "../shared"],
      },
    },
    base: "http://localhost:4174",
    plugins: [
      federation({
        filename: "remoteEntryRemote.js",
        name: "remoteCounterApp",
        exposes: {
          "./remote-app": "./src/App.vue",
          "./remote-button": "./src/components/Button.vue",
        },
        shared: {
          vue: {
            singleton: true, 
            requiredVersion: "^3"
          },
        }
      }),
      vue({}),
      vueJsx({}),
    ],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "src"),
        shared: path.resolve(__dirname, "../shared/shared"),
      },
    },
  };
});
