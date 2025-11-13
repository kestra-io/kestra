### Flow properties

Kestra lets you define and automate flows using simple YAML syntax. Each flow requires an `id`, `namespace`, and `tasks`. Other fields like `triggers`, `labels`, `inputs`, and more are optional.

Expand to read more about each of these flow properties.

| Property                     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                         | The [flow identifier](https://kestra.io/docs/workflow-components/flow) which represents the name of the flow. This ID must be unique within a namespace and is immutable (you cannot rename the flow ID later, but you can recreate it with a new name).                                                                                                                                                                                                                                                                                                                               |
| `namespace`                  | Each flow lives in one [namespace](https://kestra.io/docs/workflow-components/namespace). Namespaces are used to group flows and provide structure. Some concepts in Kestra, such as [Namespace Files](https://kestra.io/docs/concepts/namespace-files) or [KV Store](https://kestra.io/docs/concepts/kv-store) are tied to a namespace.  You cannot change a flow’s namespace after creation; create a new flow with the desired namespace and delete the old one.                                                                                                                                                                                                    |
| `tasks`                      | The list of [tasks](https://kestra.io/docs/workflow-components/tasks) to be executed. Tasks are atomic actions in your flows. By default, they will run sequentially one after the other. However, you can use additional [Flowable tasks](https://kestra.io/docs/tutorial/flowable) to run some tasks in parallel.                                                                                                                                                                                                                                                                    |
| `inputs`                     | The list of strongly-typed [inputs](https://kestra.io/docs/workflow-components/inputs) that allow you to make your flows more dynamic and reusable. Instead of hardcoding values in your flow, you can use inputs to run multiple Executions of your flow with different values determined at runtime. Use the syntax `{{ inputs.your_input_name }}` to access specific input values in your tasks.                                                                                                                                                                                |
| `outputs`                    | Each flow can [produce outputs](https://kestra.io/docs/workflow-components/outputs) that can be consumed by other flows. This is a list property, so that your flow can produce as many [outputs](https://kestra.io/docs/workflow-components/outputs) as you need. Each output needs to have an `id` (the name of the output), a `type` (the same types you know from `inputs` e.g. `STRING`, `URI` or `JSON`) and `value` which is the actual output value that will be stored in internal storage and passed to other flows when needed.                                             |
| `labels`                     | Key-value pairs that you can use to organize your flows based on your project, maintainers, or any other criteria. You can use [labels](https://kestra.io/docs/workflow-components/labels) to filter Executions in the UI.                                                                                                                                                                                                                                                                                                                                                             |
| `description`                | The [description](https://kestra.io/docs/workflow-components/descriptions) of the flow.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `variables`                  | The list of [variables](https://kestra.io/docs/workflow-components/variables) (such as API endpoints, table names, download URLs, etc.) that you can access within tasks using the syntax `{{ vars.your_variable_name }}`. Variables help reuse some values across tasks.                                                                                                                                                                                                                                                                                                              |
| `sla`                     | The list of [SLA conditions](https://kestra.io/docs/workflow-components/sla) specifying an execution `behavior` if the workflow doesn't satisfy the assertion defined in the SLA.                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `errors`                     | The list of [error tasks](https://kestra.io/docs/workflow-components/errors) that will run if there is an error in the current execution.                                                                                                                                                                                                                                                                                                          |
| `finally`                    | The list of [finally tasks](https://kestra.io/docs/workflow-components/finally) that will run after the workflow is complete. These tasks will run regardless of whether the workflow was successful or not.                                                                                                                                                                                               |
| `afterExecution`             | The list of [afterExecution](https://kestra.io/docs/workflow-components/afterexecution) tasks that will run after the execution finishes, regardless of the final state. These tasks will run after execution of the workflow reaches a final state, including the execution of the tasks from the `finally` block.                                                                                                                                                                                               |
| `disabled`                   | Set it to `true` to temporarily [disable](https://kestra.io/docs/workflow-components/disabled) any new executions of the flow. This is useful when you want to stop a flow from running (even manually) without deleting it. Once you set this property to true, nobody will be able to create any execution of that flow, whether from the UI or via an API call, until the flow is re-enabled by setting this property back to `false` (default behavior) or by deleting this property.                                                                                              |
| `revision`                   | The [flow version](https://kestra.io/docs/concepts/revision), managed internally by Kestra, and incremented upon each modification. You should **not** manually set it.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `triggers`                   | The list of [triggers](https://kestra.io/docs/workflow-components/triggers) which automatically start a flow execution based on events, such as a scheduled date, a new file arrival, a new message in a queue, or the completion event of another flow's execution.                                                                                                                                                                                                                                                                                                                   |
| `pluginDefaults`               | The list of [default values](https://kestra.io/docs/workflow-components/plugin-defaults), allowing you to avoid repeating the same plugin properties. Using `values`, you can set the default properties. The `type` is a full qualified Java class name, e.g. `io.kestra.plugin.core.log.Log`, but you can use a prefix e.g. `io.kestra` to apply some properties to all tasks. If `forced` is set to `true`, the `pluginDefault` will take precedence over properties defined in the task (the default behavior is `forced: false`).                                                                                  |
| `concurrency`                | Control the number of [concurrent executions](https://kestra.io/docs/workflow-components/concurrency) of a given flow by setting the `limit` key. Executions beyond that limit will be queued by default — you can customize that by configuring the `behavior` property which can be set to `QUEUE` (default), `CANCEL` or `FAIL`.                                                                                                                                                                                                                        |
| `retry`                    | Set a flow-level `retry` policy to restart the execution if any task fails. The retry `behavior` is customizable — you can choose to `CREATE_NEW_EXECUTION` or `RETRY_FAILED_TASK` (default). Only with the `CREATE_NEW_EXECUTION` behavior, the `attempt` of the execution is incremented. Otherwise, only the failed task run is restarted (incrementing the attempt of the task run rather than the execution). Apart from the `behavior` property, the `retry` policy is identical to [task retries](https://kestra.io/docs/workflow-components/retries). |

### Plugin documentation

To inspect properties of a **specific plugin**, click anywhere in that task, trigger or plugin code within the editor. The task documentation will load in this view.

### Task properties

Each flow consists of **tasks**. The following core properties are available to all tasks.

| Property       | Description                                                                                                                                                                                                                                                                                                                                                   |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`           | A unique identifier of the task                                                                                                                                                                                                                                                                                                                              |
| `type`         | A full Java class name that represents the type of the task                                                                                                                                                                                                                                                                                                       |
| `description`  | Your custom [documentation](https://kestra.io/docs/workflow-components/descriptions) of what the task does                                                                                                                                                                                                                                                    |
| `retry`        | How often should the task be retried in case of a failure, and the [type of retry strategy](https://kestra.io/docs/workflow-components/retries)                                                                                                                                                                                                               |
| `timeout`      | The [maximum time allowed](https://kestra.io/docs/workflow-components/timeout) for the task to complete                                                                                                                                                                                                                                                       |
| `runIf`      | Skip a task if the provided condition evaluates to false                                                                                                                                                                                                                                                       |
| `disabled`     | A boolean flag indicating whether the task is [disabled or not](https://kestra.io/docs/workflow-components/disabled); if set to `true`, the task will be skipped during the execution                                                                                                                                                                         |
| `workerGroup`  | The [group of workers](https://kestra.io/docs/enterprise/worker-group) (EE-only) that are eligible to execute the task; you can specify a `workerGroup.key` and a `workerGroup.fallback` (the default is `WAIT`)                                                                                                                                                              |
| `allowFailure` | A boolean flag allowing to continue the execution even if this task fails                                                                                                                                                                                                                                                                                     |
| `allowWarning` | A boolean flag allowing to mark a task run as Success despite Warnings                                                                                                                                                                                                                                                                                     |
| `logLevel`     | The level of log detail to be stored.                                                                                                                                                                                                                                                                                                                         |
| `logToFile`     | A boolean that lets you store logs as a file in internal storage. That file can be previewed and downloaded from the Logs and Gantt Execution tabs. When set to `true`, logs aren’t saved in the database, which is useful for tasks that produce a large amount of logs that would otherwise take up too much space. The same property can be set on triggers. |


### Input types

Inputs in Kestra are strongly typed. Each type defines how values are entered, validated, and rendered in the UI. Below are all supported input types and their validation rules.

| Property           | Description                                                                                                                                                                                                                                  |
| ------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `STRING`           | Any string value. Values are passed without parsing. You can add a `validator` regex for custom validation.           |
| `INT`              | Integer without decimals. Supports `min` and `max` validation to enforce numeric ranges. Example: `42`.                                                                                                                                      |
| `FLOAT`            | Floating-point number with decimals. Supports `min` and `max` validation. Example: `3.14`.                                                                                                                                                   |
| `BOOL` | Boolean flag, must be `true` or `false`. Avoid scalar equivalents such as `yes`/`no`, as the API and UI expect `true` or `false`.                                                                                        |
| `SELECT`           | Single value chosen from a predefined list, either static `values` or a dynamic list defined via an `expression`, which can render values using `kv()` or `http()` functions. Supports `allowCustomValue` to let user enter a custom value and `autoSelectFirst` to preselect the first item. |
| `MULTISELECT`      | One or more values chosen from a predefined list, either static `values` or a dynamic list defined via an `expression`, which can render values using `kv()` or `http()` functions. Supports `allowCustomValue` to let user enter a custom value and `autoSelectFirst` to preselect the first item.                                                                                     |
| `DATE`             | ISO-8601 date (`YYYY-MM-DD`). Supports `after` and `before` validation to enforce valid ranges. Example: `2042-12-28`.                                                                                                                       |
| `TIME`             | ISO-8601 time (`HH:MM:SS`) without timezone. Supports `after` and `before` validation. Example: `10:15:30`.                                                                                                                                  |
| `DATETIME`         | ISO-8601 datetime with timezone in UTC format (`YYYY-MM-DDTHH:MM:SSZ`). Supports `after` and `before` validation. Example: `2042-04-02T04:20:42.000Z`.                                                                                       |
| `DURATION`         | ISO-8601 duration string, e.g. `PT5M6S`. Supports `min` and `max` duration bounds.                                                                                                                                                           |
| `FILE`             | File upload via `multipart/form-data`. Uploaded files are stored in Kestra’s internal storage and exposed as `kestra:///...` URIs. You can pass Namespace Files as `defaults` using the protocol: `defaults: nsfile:///hello.txt`.                                                                               |
| `JSON`             | Valid JSON string, parsed into a typed object. Example: `{"name": "kestra"}`.                                                                                                                                                                |
| `YAML`             | Valid YAML string, parsed into a typed object. Example: `- user: John`.                                                                                                                                                                      |
| `URI`              | Valid URI string. Example: `https://kestra.io/docs`.                                                                                                                                                                                   |
| `SECRET`           | Encrypted string stored in the database. Decrypted at runtime and masked in UI/execution context. Requires an [encryption key](https://kestra.io/docs/configuration#encryption).                                                                        |
| `ARRAY`            | JSON array or YAML list. Must define `itemType` (e.g., `INT`, `STRING`, `DATETIME`). Each item is validated against its type. Example defaults: `[1, 2, 3]` or YAML list.                                                                    |


### Input properties

All input types share a common set of properties. These define how inputs behave, whether they are required, and how they appear in the UI.

| Property          | Description                                                                                                                                        |
| ----------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `id`              | Unique identifier for the input. Used to access the input at runtime e.g. `{{ inputs.myInput }}`. |
| `type`            | The input type, chosen from the supported list e.g. `STRING`, `INT`, `FLOAT`, etc.                                                                  |
| `required`        | Whether the input must be provided. If `required: true`, the execution must have a value, either provided at runtime or via `defaults`.                                      |
| `defaults`        | Default value to use if no value is provided at runtime. Must match the declared type.                                    |
| `dependsOn`       | Makes this input dependent on an array of `inputs` with optional `condition` to show inputs in the UI only when needed.                                          |
| `displayName`     | Human-readable label for the input in the UI.                                                                                                      |
| `description`     | Markdown description rendered in the UI when executing the flow.                                                                                                           |
| `expression`      | Pebble expression to fetch dynamic values e.g. using `kv()` or `http()` functions. Commonly used with `SELECT` or `MULTISELECT`.                             |
| `autoSelectFirst` | If `true`, auto-selects the first option from a dropdown when no default is set (applies to `SELECT` and `MULTISELECT`).                           |


### Flow example

Here's an example flow showing how to use `labels`, `inputs`, `variables`, `triggers` and `description`.

```yaml
id: getting_started
namespace: company.team
description: Let's `write` some **markdown**

labels:
  team: data
  owner: kestrel
  project: falco
  environment: dev
  country: US

inputs:
  - id: user
    type: STRING
    required: false
    prefill: Kestrel
    description: This is an optional input — if not set at runtime, it will use the default value Kestrel

  - id: run_task
    type: BOOL
    defaults: true

  - id: pokemon
    type: MULTISELECT
    displayName: Choose your favorite Pokemon
    description: You can pick more than one!
    values:
      - Pikachu
      - Charizard
      - Bulbasaur
      - Psyduck
      - Squirtle
      - Mewtwo
      - Snorlax
    dependsOn:
      inputs:
        - run_task
      condition: "{{ inputs.run_task }}"

  - id: bird
    type: SELECT
    displayName: Choose your favorite Falco bird
    values:
      - Kestrel
      - Merlin
      - Peregrine Falcon
      - American Kestrel
    dependsOn:
      inputs:
        - user
      condition: "{{ inputs.user == 'Kestrel' }}"

variables:
  first: 1
  second: "{{ vars.first }} < 2"

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    description: this is a *task* documentation
    message: |
      The variables we used are {{ vars.first }} and {{ render(vars.second) }}.
      The input is {{ inputs.user }} and the task was started at {{ taskrun.startDate }} from flow {{ flow.id }}.

  - id: run_if_true
    type: io.kestra.plugin.core.debug.Return
    format: Hello World!
    runIf: "{{ inputs.run_task }}"

  - id: fallback
    type: io.kestra.plugin.core.debug.Return
    format: fallback output

finally:
  - id: finally_log
    type: io.kestra.plugin.core.log.Log
    message: "This task runs after all the tasks are run, irrespective of whether the tasks ran successfully or failed. Execution {{ execution.state }}" # Execution RUNNING

afterExecution:
  - id: afterExecution_log
    type: io.kestra.plugin.core.log.Log
    message: "This task runs after the flow execution is complete. Execution {{ execution.state }}" # Execution FAILED / SUCCESS

outputs:
  - id: flow_output
    type: STRING
    value: "{{ tasks.run_if_true.state != 'SKIPPED' ? outputs.run_if_true.value : outputs.fallback.value }}"

pluginDefaults:
  - type: io.kestra.plugin.core.log.Log
    values:
      level: TRACE

triggers:
  - id: monthly
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 9 1 * *" # 1st of each month at 9am
```

You can document flows, tasks, inputs, or triggers with the `description` property. These descriptions are rendered in the UI using [Markdown](https://en.wikipedia.org/wiki/Markdown).

### Pebble templating

Kestra has a [Pebble templating engine](https://kestra.io/docs/concepts/pebble) allowing you to dynamically render variables, inputs and outputs within the execution context using [Pebble expressions](https://kestra.io/docs/concepts/expression). For example, the `{{ flow.namespace }}` expression allows accessing the namespace of the current flow and the `{{ printContext() }}` function allows you to print the entire context of the execution, which is useful for debugging.

### Pebble functions, filters and tags

| Filter           | Example and Description                                                                                                          |
|------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `abs`            | `{{ -7 \| abs }}` — Returns the absolute value of -7, resulting in 7.                                                            |
| `abbreviate`     | `{{ "this is a long sentence." \| abbreviate(7) }}` — Shortens a string using an ellipsis. The length includes the ellipsis. Can be chained with other filters: `{{ "apple" \| upper \| abbreviate(3) }}`. |
| `appLink`        | `{{ appLink('yourAppId') }}` — Fetch the URL of the App (EE-only) linked to the current Execution. To get the base URL of the app allowing to create new Executions, add `baseUrl=true` e.g. `{{ appLink('yourAppId', baseUrl=true) }}`. If there is only one App linked to the flow, you can skip the App ID argument e.g. `{{ appLink() }}`. |
| `base64Decode`   | `{{ "aGVsbG8=" \| base64Decode }}` — Decodes the base64 string, resulting in "hello".                                            |
| `base64decode`   | `{{ "dGVzdA==" \| base64decode }}` — Decodes a base64-encoded string into UTF-8. |
| `base64Encode`   | `{{ "hello" \| base64Encode }}` — Encodes the string in base64, resulting in "aGVsbG8=".                                         |
| `base64encode`   | `{{ "test" \| base64encode }}` — Encodes a string to base64. |
| `block`          | `{{ block("post") }}` — Renders the contents of the "post" block in a template `{% block header %} Introduction {% endblock %}` |
| `capitalize`     | `{{ "hello" \| capitalize }}` — Capitalizes the first letter, resulting in "Hello".                                              |
| `for`            | `{% for user in users %}{{ loop.index }} - {{ user.id }}{% endfor %}` — Iterates over a list of values in a template. Supports else block for empty collections: `{% for user in users %} ... {% else %} ... {% endfor %}`. |
| `if`             | `{% if users is empty %} ... {% endif %}` — Conditional block based on an expression in a template. Supports multiple branches: `{% if category == "news" %} ... {% elseif category == "sports" %} ... {% else %} ... {% endif %}`. |
| `macro`          | `{% macro input(type, name) %} ... {% endmacro %}` — Creates a reusable template fragment. Invoke like a function: `{{ input("text", name="Mitchell") }}`. |
| `raw`            | `{% raw %}{{ user.name }}{% endraw %}` — Writes a block of syntax that won't be parsed in a template. |
| `set`            | `{% set header = "Test Page" %}` — Defines a variable in the current context in a template. |
| `chunk`          | `{{ [1, 2, 3, 4] \| chunk(2) }}` — Splits the list into chunks of size 2, resulting in [[1, 2], [3, 4]].                         |
| `className`      | `{{ "12.3" \| number \| className }}` — Returns the class name of an object. |
| `contains`       | `{{ ["apple", "pear", "banana"] contains "apple" }}` — Checks if a collection contains a particular item. |
| `currentEachOutput` | `{{ currentEachOutput(outputs.first) }}` — Retrieves the current output of a sibling task. |
| `date`           | `{{ execution.startDate \| date("yyyy-MM-dd") }}` — Formats the date as "yyyy-MM-dd". Can use named arguments: `{{ stringDate \| date('yyyy/MMMM/d', existingFormat='yyyy-MMMM-d') }}`. Can format timestamps with timezone: `{{ 1378653552123 \| date(format="iso_milli", timeZone="Europe/Paris") }}`. |
| `dateAdd`        | `{{ execution.startDate \| dateAdd(1, "DAYS") }}` — Adds 1 day to the date. Can be used with null-coalescing: `{{ trigger.date ?? execution.startDate \| dateAdd(-1, 'DAYS') }}`. |
| `default`        | `{{ myVar \| default("default value") }}` — Returns "default value" if `myVar` is null or empty.                                 |
| `distinct`       | `{{ ['1', '1', '2', '3'] \| distinct }}` — Returns a list of unique elements, resulting in [1, 2, 3].                            |
| `escapeChar`     | `{{ "Can't be here" \| escapeChar('single') }}` — Escapes special characters in a string. |
| `errorLogs`      | `{{ errorLogs() }}` — Prints all error logs from the current execution. |
| `fileExists`     | `{{ fileExists(output.download.uri) }}` — Returns true if file is present at the given uri location.                             |
| `fileSize`       | `{{ fileSize(output.download.uri) }}` — Returns the size of the file present at the given uri location.                          |
| `filter`         | `{% filter upper %}hello{% endfilter %}` — Applies a filter to a chunk of template content. |
| `first`          | `{{ [1, 2, 3] \| first }}` — Returns the first element of the list, resulting in 1.                                              |
| `flatten`        | `{{ [[1, 2], [3, 4]] \| flatten }}` — Flattens a nested list, resulting in [1, 2, 3, 4].                                         |
| `fromIon`        | `{{ fromIon(read(someItem)).someField }}` — Converts an ION string to an object and accesses its properties. |
| `fromJson`       | `{{ fromJson('{"foo": [666, 1, 2]}').foo[0] }}` — Converts a JSON string to an object and accesses its properties. |
| `http`           | `{{ http(uri = 'https://dummyjson.com/products/categories') }}` — Fetches data from an external API directly. |
| `indent`         | `{{ "Hello\nworld" \| indent(4) }}` — Adds 4 spaces before each line except the first, resulting in "Hello\n    world".          |
| `isFileEmpty`    | `{{ isFileEmpty(output.download.uri) }}` — Returns true if file present at the given uri location is empty.                      |
| `isIn`           | `{{ execution.state isIn ['SUCCESS', 'KILLED', 'CANCELLED'] }}` — Returns true if the value on the left is present in the list on the right. Useful for conditions such as `runIf`. |
| `is defined`     | `{% if missing is not defined %} ... {% endif %}` — Checks if a variable is defined in a template. |
| `is empty`       | `{% if user.email is empty %} ... {% endif %}` — Checks if a variable is empty in a template. |
| `is even`        | `{% if 2 is even %} ... {% endif %}` — Checks if an integer is even in a template. |
| `is iterable`    | `{% if users is iterable %} ... {% endif %}` — Checks if a variable is iterable in a template. |
| `is json`        | `{% if '{"test": 1}' is json %} ... {% endif %}` — Checks if a variable is a valid JSON string in a template. |
| `is map`         | `{% if {"apple":"red", "banana":"yellow"} is map %} ... {% endif %}` — Checks if a variable is a map in a template. |
| `is null`        | `{% if user.email is null %} ... {% endif %}` — Checks if a variable is null in a template. |
| `is not even`    | `{% if 3 is not even %} ... {% endif %}` — Negates a boolean expression in a template. |
| `is odd`         | `{% if 3 is odd %} ... {% endif %}` — Checks if an integer is odd in a template. |
| `join`           | `{{ ["a", "b", "c"] \| join(",") }}` — Joins the list into a string, resulting in "a,b,c".                                       |
| `jq`             | `{{ myObject \| jq(".foo") }}` — Applies JQ expression to extract the "foo" property from `myObject`.                            |
| `keys`           | `{{ {"a": 1, "b": 2} \| keys }}` — Returns the keys of the map, resulting in ["a", "b"].                                         |
| `kv`             | By default, retrieves a KV pair from the current namespace: `{{ kv('MY_KEY') }}`. If a namespace is provided as second argument, retrieves from that namespace: `{{ kv('MY_KEY', 'company.team') }}`. Can also use named parameters to specify error handling: `{{ kv(key='KEY_ID', namespace='NAMESPACE_ID', errorOnMissing=false) }}` returns null instead of error when key is missing. |
| `last`           | `{{ [1, 2, 3] \| last }}` — Returns the last element of the list, resulting in 3.                                                |
| `length`         | `{{ "Hello" \| length }}` — Returns the length of "Hello", which is 5.                                                           |
| `lower`          | `{{ "HELLO" \| lower }}` — Converts the string to lowercase, resulting in "hello".                                               |
| `max`            | `{{ max(user.age, 80) }}` — Returns the largest of its numerical arguments. |
| `md5`            | `{{ "hello" \| md5 }}` — Computes the MD5 hash of the string.                                                                    |
| `merge`          | `{{ [1, 2] \| merge([3, 4]) }}` — Merges two lists, resulting in [1, 2, 3, 4].                                                   |
| `min`            | `{{ min(user.age, 80) }}` — Returns the smallest of its numerical arguments. |
| `nindent`        | `{{ "Hello\nworld" \| nindent(4) }}` — Adds a newline and then 4 spaces before each line, resulting in "\n    Hello\n    world". |
| `number`         | `{{ "123" \| number }}` — Parses the string "123" into the number 123.                                                           |
| `numberFormat`   | `{{ 12345.6789 \| numberFormat("###,###.##") }}` — Formats the number 12345.6789 as "12,345.68".                                 |
| `now`            | `{{ now() }}` — Returns the current datetime. Can specify timezone: `{{ now(timeZone='Europe/Paris') }}` or format: `{{ now(format='sql_milli') }}`. |
| `parent`         | `{{ parent() }}` — Renders the content of the parent block. |
| `printContext`   | `{{ printContext() }}` — Prints the entire context of the execution, useful for debugging. |
| `randomInt`      | `{{ randomInt(1, 10) }}` — Generates a random integer from a specified range. |
| `randomPort`     | `{{ randomPort() }}` — Generate a random available port. |
| `range`          | `{{ range(0, 3) }}` — Generates a list from 0 to 3. |
| `read`           | `{{ read('subdir/file.txt') }}` — Reads an internal storage file and returns its content as a string. |
| `render`         | `{{ render(namespace.github.token) }}` — Recursively renders the variable containing Pebble expressions. |
| `renderOnce`     | `{{ renderOnce(expression_string) }}` — Renders nested Pebble expressions only once. |
| `replace`        | `{{ "Hello world!" \| replace({'world': 'Kestra'}) }}` — Replaces "world" with "Kestra", resulting in "Hello, Kestra!".          |
| `reverse`        | `{{ [1, 2, 3] \| reverse }}` — Reverses the list, resulting in [3, 2, 1].                                                        |
| `rsort`          | `{{ [3, 1, 2] \| rsort }}` — Sorts the list in reverse order, resulting in [3, 2, 1].                                            |
| `secret`         | `{{ secret('MY_SECRET') }}` — Retrieves secret `MY_SECRET`. |
| `sha1`           | `{{ "hello" \| sha1 }}` — Computes the SHA-1 hash of the string.                                                                 |
| `sha256`         | `{{ "hello" \| sha256 }}` — Computes the SHA-256 hash of the string.                                                             |
| `sha512`         | `{{ "hello" \| sha512 }}` — Computes the SHA-512 hash of the string.                                                             |
| `slice`          | `{{ "Hello, world!" \| slice(0, 5) }}` — Extracts a substring, resulting in "Hello".                                             |
| `slugify`        | `{{ "Hello World!" \| slugify }}` — Converts a string to a URL-friendly format. |
| `sort`           | `{{ [3, 1, 2] \| sort }}` — Sorts the list in ascending order, resulting in [1, 2, 3].                                           |
| `split`          | `{{ "a,b,c" \| split(",") }}` — Splits the string into a list, resulting in ["a", "b", "c"].                                     |
| `startsWith`     | `{{ "hello world" \| startsWith("hello") }}` — Checks if a string starts with a given prefix. |
| `string`         | `{{ 123 \| string }}` — Converts 123 into a string.                                                                              |
| `substringAfter` | `{{ "a.b.c" \| substringAfter(".") }}` — Extracts the substring after the first occurrence of a separator. |
| `substringAfterLast` | `{{ "a.b.c" \| substringAfterLast(".") }}` — Extracts the substring after the last occurrence of a separator. |
| `substringBefore` | `{{ "a.b.c" \| substringBefore(".") }}` — Extracts the substring before the first occurrence of a separator. |
| `substringBeforeLast` | `{{ "a.b.c" \| substringBeforeLast(".") }}` — Extracts the substring before the last occurrence of a separator. |
| `tasksWithState` | `{{ tasksWithState('failed') }}` — Returns a map of tasks and their states. |
| `timestamp`      | `{{ execution.startDate \| timestamp }}` — Converts the date to a Unix timestamp in seconds.                                     |
| `timestampMicro` | `{{ execution.startDate \| timestampMicro }}` — Converts the date to a Unix timestamp in microseconds.                           |
| `timestampMilli` | `{{ execution.startDate \| timestampMilli }}` — Converts the date to a Unix timestamp in milliseconds.                           |
| `timestampNano`  | `{{ execution.startDate \| timestampNano }}` — Converts the date to a Unix timestamp in nanoseconds.                             |
| `title`          | `{{ "article title" \| title }}` — Capitalizes the first letter of each word. |
| `toIon`          | `{{ myObject \| toIon }}` — Converts `myObject` into an ION string.                                                               |
| `toJson`         | `{{ myObject \| toJson }}` — Converts `myObject` into a JSON string.                                                             |
| `trim`           | `{{ " Hello " \| trim }}` — Trims leading and trailing whitespace, resulting in "Hello".                                         |
| `upper`          | `{{ "hello" \| upper }}` — Converts the string to uppercase, resulting in "HELLO". Can be chained with other filters: `{{ "When life gives you lemons, make lemonade." \| upper \| abbreviate(13) }}`. |
| `urlDecode`      | `{{ "a%20b" \| urlDecode }}` — URL decodes the string, resulting in "a b".                                                       |
| `urlEncode`      | `{{ "a b" \| urlEncode }}` — URL encodes the string, resulting in "a%20b".                                                       |
| `uuid`           | `{{ uuid() }}` — Generates a UUID in the Kestra format (i.e., a UUID encoded in Url62). |
| `values`         | `{{ {'foo': 'bar', 'baz': 'qux'} \| values }}` — Retrieves the values from a map. |
| `yaml`           | `{{ yaml('foo: [666, 1, 2]').foo[0] }}` — Converts a YAML string to an object and accesses its properties. |


### Common Pebble expressions 

| Expression                                                                                         | Description                                                                                                                                                                                                                                                                                           |
|----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `{{ flow.id }}`                                                                                    | The identifier of the flow.                                                                                                                                                                                                                                                                           |
| `{{ flow.namespace }}`                                                                             | The name of the flow namespace.                                                                                                                                                                                                                                                                       |
| `{{ flow.tenantId }}`                                                                              | The identifier of the tenant.                                                                                                                                                                                                                                                               |
| `{{ flow.revision }}`                                                                              | The revision of the flow.                                                                                                                                                                                                                                                                             |
| `{{ execution.id }}`                                                                               | The execution ID, a generated unique id for each execution.                                                                                                                                                                                                                                           |
| `{{ execution.startDate }}`                                                                        | The start date of the current execution, can be formatted with `{{ execution.startDate \| date('yyyy-MM-dd HH:mm:ss.SSSSSS') }}`.                                                                                                                                                                     |
| `{{ execution.originalId }}`                                                                       | The original execution ID, this id will never change even in case of replay and keep the first execution ID.                                                                                                                                                                                          |
| `{{ execution.outputs }}`                                                                          | The outputs of the execution as defined in the flow outputs, only populated when the execution is terminated (`finally` or `afterExecution` block).                                                                                                                                                    |
| `{{ task.id }}`                                                                                    | The current task ID.                                                                                                                                                                                                                                                                                  |
| `{{ task.type }}`                                                                                  | The current task Type (Java fully qualified class name).                                                                                                                                                                                                                                              |
| `{{ taskrun.id }}`                                                                                 | The current task run ID.                                                                                                                                                                                                                                                                              |
| `{{ taskrun.startDate }}`                                                                          | The current task run start date.                                                                                                                                                                                                                                                                      |
| `{{ taskrun.parentId }}`                                                                           | The current task run parent identifier. Only available with tasks inside a Flowable Task.                                                                                                                                                                                                             |
| `{{ taskrun.value }}`                                                                              | The value of the current task run, only available with tasks inside in Flowable Tasks.                                                                                                                                                                                                                |
| `{{ taskrun.iteration }}`                                                                          | The index of the current task run iteration, only available with tasks inside in Flowable Tasks.                                                                                                                                                                                                      |
| `{{ taskrun.attemptsCount }}`                                                                      | The number of attempts for the current task (when retry or restart is performed).                                                                                                                                                                                                                     |
| `{{ parent.taskrun.value }}`                                                                       | The value of the closest (first) parent task run, only available with tasks inside a Flowable Task.                                                                                                                                                                                                   |
| `{{ parent.outputs }}`                                                                             | The outputs of the closest (first) parent task run Flowable Task, only available with tasks wrapped in a Flowable Task.                                                                                                                                                                               |
| `{{ parents }}`                                                                                    | The list of parent tasks, only available with tasks wrapped in a Flowable Task.                                                                                                                                                                                                                       |
| `{{ labels }}`                                                                                     | The executions labels accessible by keys, for example: `{{ labels.myKey1 }}` returns the value of the `myKey1` label.                                                                                                                                                                                                                         |
| `{{ trigger.date }}`                                                                               | The date of the current schedule.                                                                                                                                                                                                                                                                     |
| `{{ trigger.next }}`                                                                               | The date of the next schedule.                                                                                                                                                                                                                                                                        |
| `{{ trigger.previous }}`                                                                           | The date of the previous schedule.                                                                                                                                                                                                                                                                    |
| `{{ trigger.executionId }}`                                                                        | The ID of the execution that triggers the current flow.                                                                                                                                                                                                                                               |
| `{{ trigger.namespace }}`                                                                          | The namespace of the flow that triggers the current flow.                                                                                                                                                                                                                                             |
| `{{ trigger.flowId }}`                                                                             | The ID of the flow that triggers the current flow.                                                                                                                                                                                                                                                    |
| `{{ trigger.flowRevision }}`                                                                       | The revision of the flow that triggers the current flow.                                                                                                                                                                                                                                              |
| `{{ envs.foo }}`                                                                                   | Accesses environment variable `ENV_FOO` (by default prefixed with `ENV_`).                                                                                                                                                                                                                                                           |
| `{{ kestra.environment }}`                                                                         | Accesses Environment variables such as `kestra.environment.name.` Must be set in your [configuration](https://kestra.io/docs/configuration#kestra-url) to be accessible.                                                                                                                              |
| `{{ kestra.url }}`                                                                                 | Accesses Environment URL variable. Must be set in your [configuration](https://kestra.io/docs/configuration#kestra-url) to be accessible.                                                                                                                                                             |
| `{{ globals.foo }}`                                                                                | Accesses global variable `foo`.                                                                                                                                                                                                                                                                       |
| `{{ vars.my_variable }}`                                                                           | Accesses flow variable `my_variable`.                                                                                                                                                                                                                                                                 |
| `{{ inputs.myInput }}`                                                                             | Accesses flow input `myInput`.                                                                                                                                                                                                                                                                        |
| `{{ namespace.env.name }}`                                                             | Accesses namespace variable `env.name`.                                                                                                                                                                                                                                                   |
| `{{ outputs.taskId.data }}`                                                             | Accesses task output attribute called `data`.                                                                                                                                                                                                                                                                       |
| `{{ "apple" ~ "pear" ~ "banana" }}`                                                                | Concatenates multiple strings, resulting in "applepearbanana".                                                                                                                                                                                                                                                                        |
| `{{ foo == null ? bar : 42 }}`                                                                    | Returns `bar` if `foo` is null, otherwise returns `42`.                                                                                                                                                                                                                                                         |
| `{{ foo ?? bar ?? baz }}`                                                                          | Returns the first non-null value: `foo` if defined, otherwise `bar`, otherwise `baz`.                                                                                                                                                                                                                                            |
| `{# THIS IS A COMMENT #}`                                                                          | Adds a comment that won't appear in the output.                                                                                                                                                                                                                                                       |
| `{{ foo.bar }}`                                                                                    | Accesses a child attribute of a variable, e.g., if `foo = {"bar": "value"}`, returns "value".                                                                                                                                                                                                                                                             |
| `{{ foo['my-key'] }}`                                                                             | Uses subscript notation to access attributes with special characters, e.g. use `foo['my-key']` instead of `foo.my-key`.                                                                                                                                                                                                                                 |
| `{{ "Hello #{who}" }}`                                                                             | String interpolation within a literal, e.g., if `who = "World"`, returns "Hello World".                                                                                                                                                                                                                                                                |

### Links to learn more

* Follow the step-by-step [tutorial](https://kestra.io/docs/tutorial)
* Check the [documentation](https://kestra.io/docs)
* Watch a 15-minute video explanation of key concepts on the [Kestra's YouTube channel](https://go.kestra.io/youtube-get-started)
* Submit a feature request or a bug report on [GitHub](https://github.com/kestra-io/kestra/issues/new/choose)
* Need help? [Join the community](https://kestra.io/slack)
* Do you like the project? Give us a ⭐️ on [GitHub](https://github.com/kestra-io/kestra).
