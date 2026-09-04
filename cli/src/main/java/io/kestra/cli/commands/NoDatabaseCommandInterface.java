package io.kestra.cli.commands;

/**
 * A command that owns no repository, so it runs in a context with no datasource and no migration
 * beans — see {@code Kestra.contextBuilder}.
 *
 * <p>
 * Only for a command that needs no database in <em>either</em> edition. Several commands that reach
 * the instance purely over HTTP in the open-source edition still resolve a tenant, or a plugin
 * manager, through a repository in the Enterprise Edition, and must not carry this marker.
 */
public interface NoDatabaseCommandInterface {
}
