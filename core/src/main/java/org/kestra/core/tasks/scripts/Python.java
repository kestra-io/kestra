package org.kestra.core.tasks.scripts;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.avro.generic.GenericData;
import org.apache.commons.io.FileUtils;
import org.kestra.core.models.annotations.Documentation;
import org.kestra.core.models.annotations.Example;
import org.kestra.core.models.annotations.InputProperty;
import org.kestra.core.models.tasks.RunnableTask;
import org.kestra.core.runners.RunContext;
import org.kestra.core.tasks.debugs.Return;
import org.slf4j.Logger;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.kestra.core.utils.Rethrow.*;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Documentation(
    description = "Execute a Python script"
)
@Example(
    title = "Single python command",
    code = {
        "commands:",
        "- echo \"The current execution is : {{execution.id}}\""
    }
)


public class Python extends Bash implements RunnableTask<Bash.Output> {


    @InputProperty(
        description = "The python interpreter to use",
        body = {
            "Set the python interpreter path to use"
        },
        dynamic = true
    )
    private String pythonPath = "/usr/bin/python3";

    @InputProperty(
        description = "Requirements are python dependencies to add to the python execution process",
        body = {
            "Python dependencies list to setup in the virtualenv"
        },
        dynamic = true
    )
    private String[] requirements;

    @InputProperty(
        description = "Requirements are python dependencies to add to the python execution process",
        body = {
            "Python dependencies list to setup in the virtualenv. Add pip.conf in here if needed."
        },
        dynamic = true
    )
    private HashMap<String, String> inputFiles;

    private volatile List<File> cleanupDirectory;

    @Override
    public Bash.Output run(RunContext runContext) throws Exception {
        return run(runContext, throwFunction((tempFiles) -> {
            // final command
            List<String> renderer = new ArrayList<>();

            if (this.exitOnFailed) {
                renderer.add("set -o errexit");
            }

            String venvPath = venvPath();

            if (inputFiles == null || inputFiles.size() == 0 || !inputFiles.containsKey("main.py")) {
                throw new Exception("Invalid input files structure, expecting inputFiles property to contain at least a main.py key with python code value.");
            }

            String tmpFilesFolder = venvPath + "-files";
            File tmpFileFolderHandler = new File(tmpFilesFolder);
            if (tmpFileFolderHandler.exists()) {
                FileUtils.deleteDirectory(tmpFileFolderHandler);
            }
            Files.createDirectories(Paths.get(tmpFilesFolder));

            cleanupDirectory = Arrays.asList(tmpFileFolderHandler,new File(venvPath));

            for (String fileName : inputFiles.keySet()) {
                String subFolder = tmpFilesFolder + "/" + new File(fileName).getParent();
                if (!new File(subFolder).exists()) {
                    Files.createDirectories(Paths.get(subFolder));
                }
                String filePath = tmpFilesFolder + "/" + fileName;
                BufferedWriter writer = new BufferedWriter(new FileWriter(filePath));
                writer.write(inputFiles.get(fileName));
                writer.close();
            }

            String requirementsAsString = "";
            if (requirements != null) {
                requirementsAsString = "./bin/pip install " + String.join(" ", requirements) + " > /dev/null";
            } else {
                requirementsAsString = "echo 'no requirements'";
            }

            renderer.addAll(Arrays.asList(
                "rm -rf " + venvPath,
                pythonPath + " -m virtualenv " + venvPath + " > /dev/null",
                "mv " + tmpFilesFolder + "/* " + venvPath,
                "cd " + venvPath,
                "./bin/pip install pip --upgrade > /dev/null",
                requirementsAsString,
                "./bin/python main.py"
            ));

            return String.join("\n", renderer);
        }));
    }

    protected void cleanup() throws IOException {
        for (File folder: cleanupDirectory) {
            FileUtils.deleteDirectory(folder);
        }
    }

    private String venvPath() {
        return "/tmp/python-venv-" + this.getId();
    }

    private LogThread readInput(Logger logger, InputStream inputStream, boolean isStdErr) {
        LogThread thread = new LogThread(logger, inputStream, isStdErr);
        thread.setName("bash-log");
        thread.start();

        return thread;
    }

    @Getter
    @Builder
    public static class PythonException extends Exception {
        public PythonException(int exitCode, List<String> stdOut, List<String> stdErr) {
            super("Command failed with code " + exitCode + " and stdErr '" + String.join("\n", stdErr) + "'");
            this.exitCode = exitCode;
            this.stdOut = stdOut;
            this.stdErr = stdErr;
        }

        private final int exitCode;
        private final List<String> stdOut;
        private final List<String> stdErr;

    }
}
