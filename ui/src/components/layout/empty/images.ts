import announcements from "./assets/visuals/announcements.png";
import apps from "./assets/visuals/apps.png";
import testSuites from "./assets/visuals/testSuites.png";
import concurrency_executions from "./assets/visuals/concurrency_executions.png";
import concurrency_limit from "./assets/visuals/concurrency_limit.png";
import dependencies from "./assets/visuals/dependencies.png";
import plugins from "./assets/visuals/plugins.png";
import triggers from "./assets/visuals/triggers.png";
import versionPlugin from "./assets/visuals/versionPlugin.png";
import panels from "./assets/visuals/panels.png";
import assets from "./assets/visuals/assets.png";

export const images: Record<string, string> = {
    announcements,
    apps,
    testSuites,
    concurrency_executions,
    concurrency_limit,
    "dependencies.FLOW": dependencies,
    "dependencies.EXECUTION": dependencies,
    "dependencies.NAMESPACE": dependencies,
    "dependencies.ASSET": dependencies,
    plugins,
    triggers,
    versionPlugin,
    panels,
    assets
};
