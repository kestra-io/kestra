# Kestra Supabase Plugin

This document describes the new Supabase plugin for Kestra that allows you to interact with Supabase databases using the REST API.

## Overview

The Supabase plugin provides five main tasks for interacting with Supabase:

1. **Query** - Execute SQL queries using stored procedures (RPC)
2. **Select** - Query data from tables with filtering and pagination
3. **Insert** - Insert data into tables with upsert support
4. **Update** - Update existing records with filtering
5. **Delete** - Delete records with filtering

## Plugin Tasks

### 1. Query Task (`io.kestra.plugin.core.supabase.Query`)

Execute SQL queries using Supabase stored procedures (RPC).

**Example:**
```yaml
id: supabase_query_example
namespace: company.team

tasks:
  - id: query_users
    type: io.kestra.plugin.core.supabase.Query
    url: https://your-project.supabase.co
    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
    functionName: get_active_users
    parameters:
      limit: 10
      status: "active"
```

### 2. Select Task (`io.kestra.plugin.core.supabase.Select`)

Query data from Supabase tables with filtering, ordering, and pagination.

**Example:**
```yaml
id: supabase_select_example
namespace: company.team

tasks:
  - id: select_users
    type: io.kestra.plugin.core.supabase.Select
    url: https://your-project.supabase.co
    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
    table: users
    select: "id,name,email,created_at"
    filter: "status=eq.active"
    order: "created_at.desc"
    limit: 50
    offset: 0
```

### 3. Insert Task (`io.kestra.plugin.core.supabase.Insert`)

Insert data into Supabase tables with support for upserts.

**Example:**
```yaml
id: supabase_insert_example
namespace: company.team

tasks:
  - id: insert_user
    type: io.kestra.plugin.core.supabase.Insert
    url: https://your-project.supabase.co
    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
    table: users
    data:
      name: "John Doe"
      email: "john@example.com"
      status: "active"
    onConflict: "email"
    resolution: "merge-duplicates"
```

### 4. Update Task (`io.kestra.plugin.core.supabase.Update`)

Update existing records in Supabase tables.

**Example:**
```yaml
id: supabase_update_example
namespace: company.team

tasks:
  - id: update_user_status
    type: io.kestra.plugin.core.supabase.Update
    url: https://your-project.supabase.co
    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
    table: users
    data:
      status: "inactive"
      updated_at: "{{ now() }}"
    filter: "id=eq.123"
    select: "id,status,updated_at"
```

### 5. Delete Task (`io.kestra.plugin.core.supabase.Delete`)

Delete records from Supabase tables.

**Example:**
```yaml
id: supabase_delete_example
namespace: company.team

tasks:
  - id: delete_inactive_users
    type: io.kestra.plugin.core.supabase.Delete
    url: https://your-project.supabase.co
    apiKey: "{{ secret('SUPABASE_API_KEY') }}"
    table: users
    filter: "status=eq.inactive&last_login=lt.2022-01-01"
    select: "id,name,email"
```

## Common Properties

All Supabase tasks share these common properties:

- **url** (required): Your Supabase project URL (e.g., `https://your-project.supabase.co`)
- **apiKey** (required): Your Supabase API key (anon key for client-side operations, service_role key for server-side)
- **schema** (optional): Database schema to use (defaults to "public")

## Authentication

The plugin supports Supabase's API key authentication. You can use either:

- **Anon key**: For client-side operations with Row Level Security (RLS)
- **Service role key**: For server-side operations with elevated privileges

Store your API keys securely using Kestra's secret management:

```yaml
apiKey: "{{ secret('SUPABASE_API_KEY') }}"
```

## Filtering

The Select, Update, and Delete tasks support PostgREST filtering syntax:

- `column=eq.value` - Equals
- `column=neq.value` - Not equals
- `column=gt.value` - Greater than
- `column=gte.value` - Greater than or equal
- `column=lt.value` - Less than
- `column=lte.value` - Less than or equal
- `column=like.*pattern*` - Pattern matching
- `column=in.(value1,value2)` - In list

Multiple filters can be combined with `&`:
```
status=eq.active&age=gte.18&city=eq.Paris
```

## Error Handling

All tasks return detailed error information including:
- HTTP status codes
- Response headers
- Raw response body for debugging

## Output

Each task returns:
- **uri**: The request URI
- **code**: HTTP status code
- **headers**: Response headers
- **rows**: Result data (when applicable)
- **size/count**: Number of affected rows
- **rawResponse**: Raw response body

## Installation

The Supabase plugin is now included in Kestra core. After building and starting Kestra, the plugin tasks will be available under the `io.kestra.plugin.core.supabase` namespace.

## Requirements

- Kestra 0.24.0 or later
- A Supabase project with API access enabled
- Valid Supabase API key

## Support

For issues or questions about the Supabase plugin, please refer to the Kestra documentation or create an issue in the Kestra repository.
