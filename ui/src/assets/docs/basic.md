### Keyboard Shortcuts

Use the shortcut `CTRL + SPACE` on Windows/Linux or `fn + control + SPACE` on Mac to trigger **autocompletion** listing available properties.

If you want to **comment out** some part of your code, use the `CTRL or ⌘ + K + C` shortcut, and to uncomment it, use `CTRL or ⌘ + K + U`. To remember it, `C` stands for `comment` and `U` stands for `uncomment`.

If you want to fold all nested properties, use `CTRL or ⌘ + K` and then `CTRL or ⌘ + 0` (note that it's zero, not `O`). To unfold again, use `CTRL or ⌘ + K` and then `CTRL or ⌘ + J`.

### Flow properties

Kestra allows you to automate complex flows using a simple declarative interface.

Flows define `tasks`, the execution order of tasks, as well as flow `inputs`, `variables`, `labels`, `triggers`, and more.

Flows are defined in YAML to keep the code portable and language-agnostic.

A flow **must** have an identifier (`id`), a `namespace`, and a list of `tasks`. All other properties are optional, incl. a `description`, `labels`, `inputs`, `outputs`, `variables`, `triggers`, and `pluginDefaults`.

The table below describes all these properties in detail.

| Property                     | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`                         | The [flow identifier](https://kestra.io/docs/workflow-components/flow) which represents the name of the flow. This ID must be unique within a namespace and is immutable (you cannot rename the flow ID later, but you can recreate it with a new name).                                                                                                                                                                                                                                                                                                                               |
| `namespace`                  | Each flow lives in one [namespace](https://kestra.io/docs/workflow-components/namespace). Namespaces are used to group flows and provide structure. Some concepts in Kestra, such as [Namespace Files](https://kestra.io/docs/concepts/namespace-files) or [KV Store](https://kestra.io/docs/concepts/kv-store) are tied to a namespace — e.g. to retrieve a KV pair from a different namespace, use the `{{ kv('MYKEY', 'mynamespace') }}` expression.  Once a flow is created, you cannot change its namespace. If you need to change the namespace of a flow, create a new flow with the desired namespace and delete the old flow.                                                                                                                                                                                                    |
| `tasks`                      | The list of [tasks](https://kestra.io/docs/workflow-components/tasks) to be executed. Tasks are atomic actions in your flows. By default, they will run sequentially one after the other. However, you can use additional [Flowable tasks](https://kestra.io/docs/tutorial/flowable) to run some tasks in parallel.                                                                                                                                                                                                                                                                    |
| `inputs`                     | The list of strongly-typed [inputs](https://kestra.io/docs/workflow-components/inputs) that allow you to make your flows more dynamic and reusable. Instead of hardcoding values in your flow, you can use inputs to trigger multiple Executions of your flow with different values determined at runtime. Use the syntax `{{ inputs.your_input_name }}` to access specific input values in your tasks.                                                                                                                                                                                |
| `outputs`                    | Each flow can [produce outputs](https://kestra.io/docs/workflow-components/outputs) that can be consumed by other flows. This is a list property, so that your flow can produce as many [outputs](https://kestra.io/docs/workflow-components/outputs) as you need. Each output needs to have an `id` (the name of the output), a `type` (the same types you know from `inputs` e.g. `STRING`, `URI` or `JSON`) and `value` which is the actual output value that will be stored in internal storage and passed to other flows when needed.                                             |
| `labels`                     | Key-value pairs that you can use to organize your flows based on your project, maintainers, or any other criteria. You can use [labels](https://kestra.io/docs/workflow-components/labels) to filter Executions in the UI.                                                                                                                                                                                                                                                                                                                                                             |
| `description`                | The [description](https://kestra.io/docs/workflow-components/descriptions) of the flow.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `variables`                  | The list of [variables](https://kestra.io/docs/workflow-components/variables) (such as an API endpoint, table name, download URL, etc.) that you can access within tasks using the syntax `{{ vars.your_variable_name }}`. Variables help reuse some values across tasks.                                                                                                                                                                                                                                                                                                              |
| `sla`                     | The list of [SLA conditions](https://kestra.io/docs/workflow-components/sla) specifying an execution `behavior` if the workflow doesn't satisfy the assertion defined in the SLA. This feature is currently in Beta so some properties might change in the next releases.                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `errors`                     | The list of [error tasks](https://kestra.io/docs/workflow-components/errors) that will run if there is an error in the current execution.                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `disabled`                   | Set it to `true` to temporarily [disable](https://kestra.io/docs/workflow-components/disabled) any new executions of the flow. This is useful when you want to stop a flow from running (even manually) without deleting it. Once you set this property to true, nobody will be able to trigger any execution of that flow, whether from the UI or via an API call, until the flow is reenabled by setting this property back to `false` (default behavior) or by deleting this property.                                                                                              |
| `revision`                   | The [flow version](https://kestra.io/docs/concepts/revision), managed internally by Kestra, and incremented upon each modification. You should **not** manually set it.                                                                                                                                                                                                                                                                                                                                                                                                                |
| `triggers`                   | The list of [triggers](https://kestra.io/docs/workflow-components/triggers) which automatically start a flow execution based on events, such as a scheduled date, a new file arrival, a new message in a queue, or the completion event of another flow's execution.                                                                                                                                                                                                                                                                                                                   |
| `pluginDefaults`               | The list of [default task values](https://kestra.io/docs/workflow-components/task-defaults), allowing you to avoid repeating the same properties on each task.                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `pluginDefaults.[].type`       | The task type is a full qualified Java class name, i.e. the task name such as `io.kestra.plugin.core.log.Log`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `pluginDefaults.[].forced`     | If set to `forced: true`, the `pluginDefault` will take precedence over properties defined in the task (the default behavior is `forced: false`).                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `pluginDefaults.[].values.xxx` | The task property that you want to be set as default.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `concurrency`                | This property allows you to control the number of [concurrent executions](https://kestra.io/docs/workflow-components/concurrency) of a given flow by setting the `limit` key. Executions beyond that limit will be queued by default — you can customize that by configuring the `behavior` property which can be set to `QUEUE` (default), `CANCEL` or `FAIL`.                                                                                                                                                                                                                        |
| `retry`                    | This property allows you set a flow-level `retry` policy to restart the execution if any task fails. The retry `behavior` is customizable — you can choose to `CREATE_NEW_EXECUTION` or `RETRY_FAILED_TASK` (default). Only with the `CREATE_NEW_EXECUTION` behavior, the `attempt` of the execution is incremented. Otherwise, only the failed task run is restarted (incrementing the attempt of the task run rather than the execution). Apart from the `behavior` property, the `retry` policy is identical to [task retries](https://kestra.io/docs/workflow-components/retries). |
| `listeners`                  | The list of listeners (deprecated).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |

### Task documentation

Each flow consists of **tasks**.

To inspect properties of a **specific task**, click anywhere in that task code within the editor. The task documentation will load in this view.

Note that you need an active Internet connection to view that documentation, as it's served via an API.

### Task properties

The following core properties are available in all tasks.

| Property       | Description                                                                                                                                                                                                                                                                                                                                                   |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id`           | A unique identifier for the task                                                                                                                                                                                                                                                                                                                              |
| `type`         | A full Java class name that represents the type of task                                                                                                                                                                                                                                                                                                       |
| `description`  | Your custom [documentation](https://kestra.io/docs/workflow-components/descriptions) of what the task does                                                                                                                                                                                                                                                    |
| `retry`        | How often should the task be retried in case of a failure, and the [type of retry strategy](https://kestra.io/docs/workflow-components/retries)                                                                                                                                                                                                               |
| `timeout`      | The [maximum time allowed](https://kestra.io/docs/workflow-components/timeout) for the task to complete                                                                                                                                                                                                                                                       |
| `runIf`      | Skip a task if the provided condition evaluates to false                                                                                                                                                                                                                                                       |
| `disabled`     | A boolean flag indicating whether the task is [disabled or not](https://kestra.io/docs/workflow-components/disabled); if set to `true`, the task will be skipped during the execution                                                                                                                                                                         |
| `workerGroup`  | The [group of workers](https://kestra.io/docs/enterprise/worker-group) that are eligible to execute the task; you can specify a `workerGroup.key` and a `workerGroup.fallback` (by default WAIT)                                                                                                                                                              |
| `allowFailure` | A boolean flag allowing to continue the execution even if this task fails                                                                                                                                                                                                                                                                                     |
| `allowWarning` | A boolean flag allowing to mark a task run as Success despite Warnings                                                                                                                                                                                                                                                                                     |
| `logLevel`     | The level of log detail to be stored.                                                                                                                                                                                                                                                                                                                         |
| `logToFile`     | A boolean that lets you store logs as a file in internal storage. That file can be previewed and downloaded from the Logs and Gantt Execution tabs. When set to true, logs aren’t saved in the database, which is useful for tasks that produce a large amount of logs that would otherwise take up too much space. The same property can be set on triggers. |



### Flow example

Here is an example flow. It uses a `Log` task available in Kestra core for testing purposes and demonstrates how to use `labels`, `inputs`, `variables`, `triggers` and `description`.

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
    defaults: Kestrel
    description: This is an optional input — if not set at runtime, it will use the default value Kestrel

  - id: run_task
    type: BOOLEAN
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

You can add documentation to flows, tasks, inputs or triggers using the `description` property in which you can use the [Markdown](https://en.wikipedia.org/wiki/Markdown) syntax. All markdown descriptions will be rendered in the UI.

### Pebble templating

Kestra has a [Pebble templating engine](https://kestra.io/docs/concepts/pebble) allowing you to dynamically render variables, inputs and outputs within the execution context using [Pebble expressions](https://kestra.io/docs/concepts/expression). For example, the `{{ flow.namespace }}` expression allows accessing the namespace of the current flow and the `{{ printContext() }}` function allows you to print the entire context of the execution, which is useful for debugging.

The table below lists common Pebble expressions and functions.

| Expression                                                                                         | Description                                                                                                                     |
|----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| `{{ printContext() }}`                                                                             | Fetch the entire execution context as a JSON object.                                                                            |
| `{{ errorLogs() }}`                                                                                | Enrich your alert notifications with context about why Execution failed incl. information about failed task runs and their error stacktraces.                                                            |
| `{{ appLink('appId') }}`                                                                           | Fetch the URL of the App linked to the current Execution. To get the base URL of the app allowing to create new Executions, add `baseUrl=true` e.g. `{{ appLink('yourAppId', baseUrl=true) }}`. If there is only one App linked to the flow, you can skip the App ID argument e.g. `{{ appLink() }}`.                                                                                          |
| `{{ flow.id }}`                                                                                    | The identifier of the flow.                                                                                                     |
| `{{ flow.namespace }}`                                                                             | The name of the flow namespace.                                                                                                 |
| `{{ flow.tenantId }}`                                                                              | The identifier of the tenant (EE only).                                                                                         |
| `{{ flow.revision }}`                                                                              | The revision of the flow.                                                                                                       |
| `{{ execution.id }}`                                                                               | The execution ID, a generated unique id for each execution.                                                                     |
| `{{ execution.startDate }}`                                                                        | The start date of the current execution, can be formatted with `{{ execution.startDate                                          | date('yyyy-MM-dd HH:mm:ss.SSSSSS') }}`. |
| `{{ execution.originalId }}`                                                                       | The original execution ID, this id will never change even in case of replay and keep the first execution ID.                    |
| `{{ task.id }}`                                                                                    | The current task ID.                                                                                                            |
| `{{ task.type }}`                                                                                  | The current task Type (Java fully qualified class name).                                                                        |
| `{{ taskrun.id }}`                                                                                 | The current task run ID.                                                                                                        |
| `{{ taskrun.startDate }}`                                                                          | The current task run start date.                                                                                                |
| `{{ taskrun.parentId }}`                                                                           | The current task run parent identifier. Only available with tasks inside a Flowable Task.                                       |
| `{{ taskrun.value }}`                                                                              | The value of the current task run, only available with tasks inside in Flowable Tasks.                                         |
| `{{ taskrun.iteration }}`                                                                          | The index of the current task run iteration, only available with tasks inside in Flowable Tasks.
| `{{ taskrun.attemptsCount }}`                                                                      | The number of attempts for the current task (when retry or restart is performed).                                               |
| `{{ parent.taskrun.value }}`                                                                       | The value of the closest (first) parent task run, only available with tasks inside a Flowable Task.                             |
| `{{ parent.outputs }}`                                                                             | The outputs of the closest (first) parent task run Flowable Task, only available with tasks wrapped in a Flowable Task.         |
| `{{ parents }}`                                                                                    | The list of parent tasks, only available with tasks wrapped in a Flowable Task.                                                 |
| `{{ labels }}`                                                                                     | The executions labels accessible by keys, for example: `{{ labels.myKey1 }}`.                                                   |
| `{{ trigger.date }}`                                                                               | The date of the current schedule.                                                                                               |
| `{{ trigger.next }}`                                                                               | The date of the next schedule.                                                                                                  |
| `{{ trigger.previous }}`                                                                           | The date of the previous schedule.                                                                                              |
| `{{ trigger.executionId }}`                                                                        | The ID of the execution that triggers the current flow.                                                                         |
| `{{ trigger.namespace }}`                                                                          | The namespace of the flow that triggers the current flow.                                                                       |
| `{{ trigger.flowId }}`                                                                             | The ID of the flow that triggers the current flow.                                                                              |
| `{{ trigger.flowRevision }}`                                                                       | The revision of the flow that triggers the current flow.                                                                        |
| `{{ envs.foo }}`                                                                                   | Accesses environment variable `KESTRA_FOO`.                                                                                     |
| `{{ globals.foo }}`                                                                                | Accesses global variable `foo`.                                                                                                 |
| `{{ vars.my_variable }}`                                                                           | Accesses flow variable `my_variable`.                                                                                           |
| `{{ inputs.myInput }}`                                                                             | Accesses flow input `myInput`.                                                                                                  |
| `{{ secret('MY_SECRET') }}`                                                                        | Retrieves secret `MY_SECRET`.                                                                                                   |
| `{{ kv('MY_KEY') }}`                                                                               | Retrieves a KV pair from the current namespace.                                                                                 |
| `{{ kv('MY_KEY', 'company.team') }}`                                                               | Retrieves a KV pair from a specific namespace.                                                                                  |
| `{{ namespace.myproject.myvariable }}`                                                             | Accesses namespace variable `myproject.myvariable`.                                                                             |
| `{{ outputs.taskId.outputAttribute }}`                                                             | Accesses task output attribute.                                                                                                 |
| `{{ range(0, 3) }}`                                                                                | Generates a list from 0 to 3.                                                                                                   |
| `{{ block("post") }}`                                                                              | Renders the contents of the ["post" block](https://kestra.io/docs/concepts/expression/function#block).                          |
| `{{ currentEachOutput(outputs.first) }}`                                                           | Retrieves the current output of a sibling task.                                                                                 |
| `{{ fromJson('{"foo": [666, 1, 2]}').foo[0] }}`                                                    | Converts a JSON string to an object and accesses its properties.                                                                |
| `{{ fromIon(read(someItem)).someField }}`                                                          | Converts a ION string to an object and accesses its properties.                                                                 |
| `{{ yaml('foo: [666, 1, 2]').foo[0] }}`                                                            | Converts a YAML string to an object and accesses its properties.                                                                |
| `{{ max(user.age, 80) }}`                                                                          | Returns the largest of its numerical arguments.                                                                                 |
| `{{ min(user.age, 80) }}`                                                                          | Returns the smallest of its numerical arguments.                                                                                |
| `{{ now() }}`                                                                                      | Returns the current datetime.                                                                                                   |
| `{{ parent() }}`                                                                                   | Renders the content of the parent block.                                                                                        |
| `{{ read('subdir/file.txt') }}`                                                                    | [Reads](https://kestra.io/docs/concepts/expression/function#read) an internal storage file and returns its content as a string. |
| `{{ render(namespace.github.token) }}`                                                             | Recursively [renders](https://kestra.io/docs/concepts/expression/function#render) the variable containing Pebble expressions.   |
| `{{ renderOnce(expression_string) }}`                                                              | Renders nested Pebble expressions only once.                                                                                    |
| `{{ "apple" ~ "pear" ~ "banana" }}`                                                                | Concatenates multiple strings.                                                                                                  |
| `{{ contains ["apple", "pear", "banana"] "apple" }}`                                               | Checks if a collection contains a particular item.                                                                              |
| `{% for user in users %}{{ loop.index }} - {{ user.id }}{% endfor %}`                              | Iterates over a list of values.                                                                                                 |
| `{% if users is empty %} ... {% endif %}`                                                          | Conditional block based on an expression.                                                                                       |
| `{% macro input(type, name) %} ... {% endmacro %}`                                                 | Creates a reusable template fragment.                                                                                           |
| `{% raw %}{{ user.name }}{% endraw %}`                                                             | Writes a block of syntax that won't be parsed.                                                                                  |
| `{% set header = "Test Page" %}`                                                                   | Defines a variable in the current context.                                                                                      |
| `{% filter upper %}hello{% endfilter %}`                                                           | Applies a filter to a chunk of template.                                                                                        |
| `{% if user.age >= 18 %} ... {% endif %}`                                                          | Comparison within a conditional block.                                                                                          |
| `{{ foo == null ? bar : baz }}`                                                                    | Ternary operator for conditional expressions.                                                                                   |
| `{{ foo ?? bar ?? baz }}`                                                                          | Null-coalescing operator to test if variables are defined.                                                                      |
| `{% for user in users %} ... {% else %} ... {% endfor %}`                                          | For loop with else block for empty collections.                                                                                 |
| `{{ 2 + 2 / ( 10 % 3 ) * (8 - 1) }}`                                                               | Basic mathematical operations.                                                                                                  |
| `{% if 3 is not even %} ... {% endif %}`                                                           | Negates a boolean expression.                                                                                                   |
| `{% if category == "news" %} ... {% elseif category == "sports" %} ... {% else %} ... {% endif %}` | Conditional statements with multiple branches.                                                                                  |
| `{{ trigger.date ?? execution.startDate \| date('yyyy-MM-dd') }}`                                  | Uses null-coalescing and date formatting together.                                                                              |
| `{{ trigger.date ?? execution.startDate \| dateAdd(-1, 'DAYS') }}`                                 | Subtracts one day from the execution start date.                                                                                |
| `{{ stringDate \| date('yyyy/MMMM/d', existingFormat='yyyy-MMMM-d') }}`                            | Uses named arguments in a filter.                                                                                               |
| `{{ "apple" \| upper \| abbreviate(3) }}`                                                          | Chains multiple filters together.                                                                                               |
| `{{ now(timeZone='Europe/Paris') }}`                                                               | Returns the current datetime in a specific timezone.                                                                            |
| `{% macro input(type='text', name, value) %} ... {% endmacro %}`                                   | Macro with default argument values.                                                                                             |
| `{# THIS IS A COMMENT #}`                                                                          | Adds a comment that won't appear in the output.                                                                                 |
| `{{ foo.bar }}`                                                                                    | Accesses a child attribute of a variable.                                                                                       |
| `{{ foo['foo-bar'] }}`                                                                             | Uses subscript notation to access attributes with special characters.                                                           |
| `{{ "Hello #{who}" }}`                                                                             | String interpolation within a literal.                                                                                          |
| `{{ "When life gives you lemons, make lemonade." \| upper \| abbreviate(13) }}`                    | Uses a filter to modify string content.                                                                                         |
| `{{ max(user.score, highscore) }}`                                                                 | Uses a function to generate new content.                                                                                        |
| `{% for article in articles %} ... {% endfor %}`                                                   | Iterates over a list with a for loop.                                                                                           |
| `{% if users is empty %} ... {% elseif users.length == 1 %} ... {% else %} ... {% endif %}`        | Uses an if statement to control template flow.                                                                                  |
| `{{ input("text", "name", "Mitchell") }}`                                                          | Invokes a macro like a function.                                                                                                |
| `{% if missing is not defined %} ... {% endif %}`                                                  | Checks if a variable is defined.                                                                                                |
| `{% if user.email is empty %} ... {% endif %}`                                                     | Checks if a variable is empty.                                                                                                  |
| `{% if 2 is even %} ... {% endif %}`                                                               | Checks if an integer is even.                                                                                                   |
| `{% if users is iterable %} ... {% endif %}`                                                       | Checks if a variable is iterable.                                                                                               |
| `{% if '{"test": 1}' is json %} ... {% endif %}`                                                   | Checks if a variable is a valid JSON string.                                                                                    |
| `{% if {"apple":"red", "banana":"yellow"} is map %} ... {% endif %}`                               | Checks if a variable is a map.                                                                                                  |
| `{% if user.email is null %} ... {% endif %}`                                                      | Checks if a variable is null.                                                                                                   |
| `{% if 3 is odd %} ... {% endif %}`                                                                | Checks if an integer is odd.                                                                                                    |


The table below lists Pebble functions and filter expressions:

| Filter           | Example and Description                                                                                                          |
|------------------|----------------------------------------------------------------------------------------------------------------------------------|
| `abs`            | `{{ -7 \| abs }}` — Returns the absolute value of -7, resulting in 7.                                                            |
| `number`         | `{{ "123" \| number }}` — Parses the string "123" into the number 123.                                                           |
| `numberFormat`   | `{{ 12345.6789 \| numberFormat("###,###.##") }}` — Formats the number 12345.6789 as "12,345.68".                                 |
| `replace`        | `{{ "Hello, world!" \| replace("world", "Kestra") }}` — Replaces "world" with "Kestra", resulting in "Hello, Kestra!".           |
| `yaml`           | `{{ myObject \| yaml }}` — Converts `myObject` into a YAML string.                                                               |
| `indent`         | `{{ "Hello\nworld" \| indent(4) }}` — Adds 4 spaces before each line except the first, resulting in "Hello\n    world".          |
| `nindent`        | `{{ "Hello\nworld" \| nindent(4) }}` — Adds a newline and then 4 spaces before each line, resulting in "\n    Hello\n    world". |
| `toJson`         | `{{ myObject \| toJson }}` — Converts `myObject` into a JSON string.                                                             |
| `toIon`          | `{{ myObject \| toIon }}` — Converts `myObject` into a ION string.                                                               |
| `jq`             | `{{ myObject \| jq(".foo") }}` — Applies JQ expression to extract the "foo" property from `myObject`.                            |
| `length`         | `{{ "Hello" \| length }}` — Returns the length of "Hello", which is 5.                                                           |
| `merge`          | `{{ [1, 2] \| merge([3, 4]) }}` — Merges two lists, resulting in [1, 2, 3, 4].                                                   |
| `reverse`        | `{{ [1, 2, 3] \| reverse }}` — Reverses the list, resulting in [3, 2, 1].                                                        |
| `rsort`          | `{{ [3, 1, 2] \| rsort }}` — Sorts the list in reverse order, resulting in [3, 2, 1].                                            |
| `slice`          | `{{ "Hello, world!" \| slice(0, 5) }}` — Extracts a substring, resulting in "Hello".                                             |
| `sort`           | `{{ [3, 1, 2] \| sort }}` — Sorts the list in ascending order, resulting in [1, 2, 3].                                           |
| `split`          | `{{ "a,b,c" \| split(",") }}` — Splits the string into a list, resulting in ["a", "b", "c"].                                     |
| `capitalize`     | `{{ "hello" \| capitalize }}` — Capitalizes the first letter, resulting in "Hello".                                              |
| `join`           | `{{ ["a", "b", "c"] \| join(",") }}` — Joins the list into a string, resulting in "a,b,c".                                       |
| `keys`           | `{{ {"a": 1, "b": 2} \| keys }}` — Returns the keys of the map, resulting in ["a", "b"].                                         |
| `date`           | `{{ execution.startDate \| date("yyyy-MM-dd") }}` — Formats the date as "yyyy-MM-dd".                                            |
| `dateAdd`        | `{{ execution.startDate \| dateAdd(1, "DAYS") }}` — Adds 1 day to the date.                                                      |
| `timestamp`      | `{{ execution.startDate \| timestamp }}` — Converts the date to a Unix timestamp in seconds.                                     |
| `timestampMicro` | `{{ execution.startDate \| timestampMicro }}` — Converts the date to a Unix timestamp in microseconds.                           |
| `timestampNano`  | `{{ execution.startDate \| timestampNano }}` — Converts the date to a Unix timestamp in nanoseconds.                             |
| `default`        | `{{ myVar \| default("default value") }}` — Returns "default value" if `myVar` is null or empty.                                 |
| `trim`           | `{{ " Hello " \| trim }}` — Trims leading and trailing whitespace, resulting in "Hello".                                         |
| `truncate`       | `{{ "Hello, world!" \| truncate(5) }}` — Truncates the string to 5 characters, resulting in "Hello".                             |
| `lower`          | `{{ "HELLO" \| lower }}` — Converts the string to lowercase, resulting in "hello".                                               |
| `upper`          | `{{ "hello" \| upper }}` — Converts the string to uppercase, resulting in "HELLO".                                               |
| `first`          | `{{ [1, 2, 3] \| first }}` — Returns the first element of the list, resulting in 1.                                              |
| `last`           | `{{ [1, 2, 3] \| last }}` — Returns the last element of the list, resulting in 3.                                                |
| `unique`         | `{{ [1, 2, 2, 3] \| unique }}` — Returns a list of unique elements, resulting in [1, 2, 3].                                      |
| `urlEncode`      | `{{ "a b" \| urlEncode }}` — URL encodes the string, resulting in "a%20b".                                                       |
| `urlDecode`      | `{{ "a%20b" \| urlDecode }}` — URL decodes the string, resulting in "a b".                                                       |
| `base64Encode`   | `{{ "hello" \| base64Encode }}` — Encodes the string in base64, resulting in "aGVsbG8=".                                         |
| `base64Decode`   | `{{ "aGVsbG8=" \| base64Decode }}` — Decodes the base64 string, resulting in "hello".                                            |
| `format`         | `{{ "Hello, %s!" \| format("world") }}` — Formats the string, resulting in "Hello, world!".                                      |
| `md5`            | `{{ "hello" \| md5 }}` — Computes the MD5 hash of the string.                                                                    |
| `sha1`           | `{{ "hello" \| sha1 }}` — Computes the SHA-1 hash of the string.                                                                 |
| `sha256`         | `{{ "hello" \| sha256 }}` — Computes the SHA-256 hash of the string.                                                             |
| `sha512`         | `{{ "hello" \| sha512 }}` — Computes the SHA-512 hash of the string.                                                             |
| `map`            | `{{ [1, 2, 3] \| map(i => i * 2) }}` — Applies a function to each element, resulting in [2, 4, 6].                               |
| `filter`         | `{{ [1, 2, 3] \| filter(i => i > 1) }}` — Filters the list based on a predicate, resulting in [2, 3].                            |
| `reduce`         | `{{ [1, 2, 3] \| reduce((a, b) => a + b, 0) }}` — Reduces the list to a single value, resulting in 6.                            |
| `zip`            | `{{ zip([1, 2], [3, 4]) }}` — Zips two lists together, resulting in [[1, 3], [2, 4]].                                            |
| `unzip`          | `{{ unzip([[1, 3], [2, 4]]) }}` — Unzips a list of lists, resulting in [[1, 2], [3, 4]].                                         |
| `flatten`        | `{{ [[1, 2], [3, 4]] \| flatten }}` — Flattens a nested list, resulting in [1, 2, 3, 4].                                         |
| `groupBy`        | `{{ [{"name": "Alice"}, {"name": "Bob"}] \| groupBy("name") }}` — Groups elements by the "name" key.                             |
| `chunk`          | `{{ [1, 2, 3, 4] \| chunk(2) }}` — Splits the list into chunks of size 2, resulting in [[1, 2], [3, 4]].                         |



### Links to learn more

* Follow the step-by-step [tutorial](https://kestra.io/docs/tutorial)
* Check the [documentation](https://kestra.io/docs)
* Watch a 15-minute video explanation of key concepts on the [Kestra's YouTube channel](https://go.kestra.io/youtube-get-started)
* Submit a feature request or a bug report on [GitHub](https://github.com/kestra-io/kestra/issues/new/choose)
* Need help? [Join the community](https://kestra.io/slack)
* Do you like the project? Give us a ⭐️ on [GitHub](https://github.com/kestra-io/kestra).
