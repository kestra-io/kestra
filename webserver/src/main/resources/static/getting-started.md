Welcome to the Code Editor!

The Embedded Code Editor lets you easily add custom scripts, queries, and configuration files along with your flow YAML configuration files.

Get started by selecting a namespace. If you type a name of a namespace that doesn't exist yet, Kestra will create it for you at runtime.

Then, add a new file, e.g., a Python script. Try adding a folder named `scripts` and a file called `hello.py` with the following content:

```python
print("Hello from the Editor!")
```

Once you added a file, you can use it in your flow. Try adding a new flow named `hello.yml` with the following content:

```yaml
id: hello
namespace: ${namespace}

tasks:
  - id: hello
    type: io.kestra.plugin.scripts.python.Script
    script: "{{ read('scripts/hello.py') }}"
```

Finally, click on the Execute button to run your flow. You should see the friendly message ``Hello from the Editor!`` in the logs.

For more information about the Code Editor, check out the [documentation](https://kestra.io/docs/developer-guide/namespace-files).