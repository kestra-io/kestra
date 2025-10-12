/**
 * Pipedrive CRM Integration Plugin for Kestra.
 *
 * <p>This plugin provides tasks to interact with Pipedrive CRM API, allowing you to:
 * <ul>
 *   <li>Manage Deals (create, update, get, search)</li>
 *   <li>Manage Persons (create/update by email, get by email)</li>
 *   <li>Manage Organizations (create)</li>
 *   <li>Manage Activities (create)</li>
 * </ul>
 *
 * <h2>Authentication</h2>
 * <p>All tasks require a Pipedrive API token. You can obtain this from your Pipedrive account settings.
 * For security, store the API token in Kestra's secret management system.</p>
 *
 * <h2>Configuration</h2>
 * <p>Most tasks accept a {@code baseUrl} parameter that defaults to {@code https://api.pipedrive.com/v1}.
 * You may need to customize this for different Pipedrive instances or regions.</p>
 *
 * <h2>Examples</h2>
 * <p>Each task class includes comprehensive examples in the {@code @Plugin} annotation.</p>
 *
 * @author Kestra Contributors
 * @since 1.0.0
 */
@io.kestra.core.models.annotations.PluginSubGroup(
    description = "This sub-group of plugins contains tasks for interacting with Pipedrive CRM.",
        categories = {PluginSubGroup.PluginCategory.OTHER, PluginSubGroup.PluginCategory.TOOL}
)
package io.kestra.plugin.pipedrive;

import io.kestra.core.models.annotations.PluginSubGroup;