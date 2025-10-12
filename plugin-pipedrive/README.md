# Kestra Pipedrive Plugin

This plugin provides tasks to interact with the [Pipedrive CRM](https://www.pipedrive.com/) API, enabling automation of common CRM operations within your Kestra workflows.

## Features

### Deal Management
- **Create Deal** (`DealsCreate`): Create new deals with custom properties
- **Update Deal** (`DealsUpdate`): Update existing deals  
- **Get Deal** (`DealsGet`): Retrieve deal information by ID
- **Search Deals** (`DealsSearch`): Search and list deals with filters

### Person Management
- **Upsert Person** (`PersonsUpsert`): Create or update persons by email
- **Get Person by Email** (`PersonsGetByEmail`): Find persons by email address

### Organization Management
- **Create Organization** (`OrganizationsCreate`): Create new organizations

### Activity Management
- **Create Activity** (`ActivitiesCreate`): Create activities (meetings, calls, tasks)

## Authentication

All tasks require a Pipedrive API token. You can obtain this from your Pipedrive account settings under Personal preferences > API.

**Important**: Store your API token securely using Kestra's secret management:

```yaml
# In your Kestra secrets
PIPEDRIVE_API_TOKEN: "your_api_token_here"
```

## Configuration

Most tasks accept these common parameters:

- `apiToken` (required): Your Pipedrive API token
- `baseUrl` (optional): API base URL, defaults to `https://api.pipedrive.com/v1`

## Quick Start Examples

### Create a Deal
```yaml
id: create_deal_example
namespace: company.crm

tasks:
  - id: create_deal
    type: io.kestra.plugin.pipedrive.tasks.DealsCreate
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    title: "Enterprise Opportunity"
    value: 25000
    currency: "USD"
    status: "open"
```

### Create or Update a Person
```yaml
id: upsert_person_example
namespace: company.crm

tasks:
  - id: upsert_person
    type: io.kestra.plugin.pipedrive.tasks.PersonsUpsert
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    name: "John Smith"
    email: "john.smith@company.com"
    phone: "+1-555-123-4567"
```

### Search for Deals
```yaml
id: search_deals_example
namespace: company.crm

tasks:
  - id: search_deals
    type: io.kestra.plugin.pipedrive.tasks.DealsSearch
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    term: "Enterprise"
    status: "open"
    limit: 50
```

### Complete Sales Pipeline Example
```yaml
id: sales_pipeline_automation
namespace: company.crm

inputs:
  - id: customer_name
    type: STRING
  - id: customer_email
    type: STRING
  - id: deal_value
    type: INT

tasks:
  # 1. Create or update person
  - id: upsert_person
    type: io.kestra.plugin.pipedrive.tasks.PersonsUpsert
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    name: "{{ inputs.customer_name }}"
    email: "{{ inputs.customer_email }}"

  # 2. Create deal
  - id: create_deal
    type: io.kestra.plugin.pipedrive.tasks.DealsCreate
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    title: "Sales Opportunity - {{ inputs.customer_name }}"
    value: "{{ inputs.deal_value }}"
    currency: "USD"
    personId: "{{ outputs.upsert_person.personId }}"

  # 3. Schedule follow-up activity
  - id: schedule_followup
    type: io.kestra.plugin.pipedrive.tasks.ActivitiesCreate
    apiToken: "{{ secret('PIPEDRIVE_API_TOKEN') }}"
    subject: "Follow-up call with {{ inputs.customer_name }}"
    type: "call"
    dueDate: "{{ now() | dateAdd(3, 'DAYS') | date('yyyy-MM-dd') }}"
    dueTime: "14:00"
    dealId: "{{ outputs.create_deal.dealId }}"
    personId: "{{ outputs.upsert_person.personId }}"
```

## Error Handling

The plugin includes comprehensive error handling:

- **Authentication errors**: Invalid API tokens will result in clear error messages
- **Rate limiting**: Pipedrive API rate limits are respected
- **Validation errors**: Input validation ensures required fields are provided
- **Network errors**: HTTP connection issues are properly handled and reported

## Task Reference

Each task includes detailed documentation with:
- Complete parameter descriptions
- Multiple usage examples
- Output specifications
- Error scenarios

See the individual task documentation for comprehensive details on each operation.

## Development and Testing

The plugin includes comprehensive unit tests using WireMock for HTTP mocking. Tests cover:
- Successful operations
- Error scenarios  
- Edge cases
- Authentication handling

## Contributing

This plugin follows Kestra's plugin development standards:
- Comprehensive documentation and examples
- Full test coverage
- Proper error handling
- Consistent API patterns

## License

This plugin is part of the Kestra project and follows the same licensing terms.