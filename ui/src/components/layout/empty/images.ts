import announcement from "../../../assets/empty_visuals/announcement.png"
import apiTokens from "../../../assets/empty_visuals/apiTokens.png"
import apps from "../../../assets/empty_visuals/apps.png"
import assets from "../../../assets/empty_visuals/assets.png"
import auditLogs from "../../../assets/empty_visuals/auditLogs.png"
import blueprints from "../../../assets/empty_visuals/blueprints.png"
import cases from "../../../assets/empty_visuals/cases.png"
import concurrencyExecutions from "../../../assets/empty_visuals/concurrencyExecutions.png"
import concurrencyFlows from "../../../assets/empty_visuals/concurrencyFlows.png"
import concurrencyLimits from "../../../assets/empty_visuals/concurrencyLimits.png"
import credentials from "../../../assets/empty_visuals/credentials.png"
import dashboards from "../../../assets/empty_visuals/dashboards.png"
import dependencies from "../../../assets/empty_visuals/dependencies.png"
import groups from "../../../assets/empty_visuals/groups.png"
import iam from "../../../assets/empty_visuals/iam.png"
import instance from "../../../assets/empty_visuals/instance.png"
import killSwitch from "../../../assets/empty_visuals/killSwitch.png"
import mcpToolFlows from "../../../assets/empty_visuals/mcpToolFlows.png"
import namespace from "../../../assets/empty_visuals/namespace.png"
import namespaceFiles from "../../../assets/empty_visuals/namespaceFiles.png"
import pluginDefaults from "../../../assets/empty_visuals/pluginDefaults.png"
import promote from "../../../assets/empty_visuals/promote.png"
import quotas from "../../../assets/empty_visuals/quotas.png"
import secrets from "../../../assets/empty_visuals/secrets.png"
import tenants from "../../../assets/empty_visuals/tenants.png"
import testSuite from "../../../assets/empty_visuals/testSuite.png"
import tests from "../../../assets/empty_visuals/tests.png"
import triggers from "../../../assets/empty_visuals/triggers.png"
import variables from "../../../assets/empty_visuals/variables.png"
import versionPlugin from "../../../assets/empty_visuals/versionPlugin.png"

/** Artwork per empty-state `type`; types without a dedicated visual fall back to the generic one. */
export const images: Record<string, string> = {
    announcements: announcement,
    apiTokens,
    apps,
    assets,
    auditlogs: auditLogs,
    blueprints,
    cases,
    concurrency_executions: concurrencyExecutions,
    concurrency_limit: concurrencyFlows,
    concurrency_limits: concurrencyLimits,
    credentials,
    dashboards,
    "dependencies.FLOW": dependencies,
    "dependencies.EXECUTION": dependencies,
    "dependencies.NAMESPACE": dependencies,
    "dependencies.ASSET": dependencies,
    groups,
    iam,
    instance,
    kill_switches: killSwitch,
    mcpToolFlows,
    namespace,
    namespaceFiles,
    policies: pluginDefaults,
    promote,
    quotas,
    secrets,
    tenants,
    tests,
    testSuites: testSuite,
    triggers,
    variables,
    versionPlugin,
}
