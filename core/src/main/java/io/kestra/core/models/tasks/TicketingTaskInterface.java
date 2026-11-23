package io.kestra.core.models.tasks;

/**
 * Marker interface for a task that opens a ticket in an external ticketing system — a GitHub issue,
 * a Jira issue, a ServiceNow incident.
 *
 * <p>
 * It carries no methods. It exists so that {@code GET /api/v1/plugins/ticketing-systems} can tell
 * which of a plugin's tasks belong in the ticketing-system catalog: a plugin typically exposes a
 * dozen tasks of which exactly one opens a ticket, and no bundle-level metadata can express that.
 * </p>
 *
 * <p>
 * Plugin authors should implement it on the task that <em>creates</em> a ticket and on nothing else —
 * commenting on, searching or updating a ticket does not make a task a ticketing task.
 * </p>
 *
 * <p>
 * This interface must never gain a method: it is backported unchanged onto the {@code releases/v1.3.x}
 * line, so a plugin compiled against either copy has to keep running against the other at runtime.
 * </p>
 */
public interface TicketingTaskInterface {
}
