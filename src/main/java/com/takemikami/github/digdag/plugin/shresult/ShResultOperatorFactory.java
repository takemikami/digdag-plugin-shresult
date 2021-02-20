package com.takemikami.github.digdag.plugin.shresult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigElement;
import io.digdag.client.config.ConfigFactory;
import io.digdag.spi.CommandContext;
import io.digdag.spi.CommandExecutor;
import io.digdag.spi.CommandRequest;
import io.digdag.spi.CommandStatus;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorContext;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.TaskExecutionException;
import io.digdag.spi.TaskResult;
import io.digdag.util.BaseOperator;
import io.digdag.util.CommandOperators;
import io.digdag.util.UserSecretTemplate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// see https://github.com/treasure-data/digdag/blob/master/digdag-standards/src/main/java/io/digdag/standards/operator/ShOperatorFactory.java
public class ShResultOperatorFactory implements OperatorFactory {

  private static Logger logger = LoggerFactory.getLogger(ShResultOperatorFactory.class);

  private final CommandExecutor exec;

  @Inject
  public ShResultOperatorFactory(CommandExecutor exec) {
    this.exec = exec;
  }

  @Override
  public String getType() {
    return "sh_result";
  }

  @Override
  public Operator newOperator(OperatorContext operatorContext) {
    return new ShResultOperator(operatorContext);
  }

  class ShResultOperator extends BaseOperator {

    // TODO extract as config params.
    final int scriptPollInterval = (int) Duration.ofSeconds(10).getSeconds();

    public ShResultOperator(OperatorContext context) {
      super(context);
    }

    @Override
    public TaskResult runTask() {
      final Config state = request.getConfig();
      Config params = state.mergeDefault(request.getConfig().getNestedOrGetEmpty("sh"));

      // save System.out
      PrintStream console = System.out;

      try {
        // prepare capture stdout
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final String utf8 = StandardCharsets.UTF_8.name();
        PrintStream ps = new PrintStream(baos);
        System.setOut(ps);

        // run script
        runCode(state);

        // capture stdout
        String stdoutData = baos.toString(utf8);

        String varName = params.get("destination_variable", String.class);
        String stdoutFormat = params.get("stdout_format", String.class);

        ConfigFactory cf = request.getConfig().getFactory();
        Config storeParams = cf.create();
        storeParams.set(varName, createVariableObjectFromStdout(stdoutData, stdoutFormat));

        return TaskResult.defaultBuilder(request)
            .storeParams(storeParams)
            .build();
      } catch (IOException | InterruptedException e) {
        throw Throwables.propagate(e);
      } finally {
        // restore System.out
        System.setOut(console);
      }
    }

    private void runCode(final Config state)
        throws IOException, InterruptedException {
      final Config params = request.getConfig()
          .mergeDefault(request.getConfig().getNestedOrGetEmpty("sh"));
      final Path projectPath = workspace.getProjectPath();
      final CommandContext commandContext = buildCommandContext(projectPath);

      final CommandStatus status;
      if (!state.has("commandStatus")) {
        // Run the code since command state doesn't exist
        status = runCommand(params, commandContext);
      } else {
        // Check the status of the running command
        final ObjectNode previousStatusJson = state.get("commandStatus", ObjectNode.class);
        status = exec.poll(commandContext, previousStatusJson);
      }

      if (status.isFinished()) {
        final int statusCode = status.getStatusCode();
        if (statusCode != 0) {
          // Remove the polling state after fetching the result so that the result fetch can be retried
          // without resubmitting the code.
          state.remove("commandStatus");
          throw new RuntimeException("Command failed with code " + statusCode);
        }
        return;
      } else {
        state.set("commandStatus", status);
        throw TaskExecutionException.ofNextPolling(scriptPollInterval, ConfigElement.copyOf(state));
      }
    }

    private CommandStatus runCommand(final Config params, final CommandContext commandContext)
        throws IOException, InterruptedException {
      final Path tempDir = workspace
          .createTempDir(String.format("digdag-sh-%d-", request.getTaskId()));
      final Path workingDirectory = workspace.getPath(); // absolute
      final Path runnerPath = tempDir.resolve("runner.sh"); // absolute

      final List<String> shell;
      if (params.has("shell")) {
        shell = params.getListOrEmpty("shell", String.class);
      } else {
        shell = ImmutableList.of("/bin/sh");
      }

      final ImmutableList.Builder<String> cmdline = ImmutableList.builder();
      if (params.has("shell")) {
        cmdline.addAll(shell);
      } else {
        cmdline.addAll(shell);
      }
      cmdline.add(workingDirectory.relativize(runnerPath).toString()); // relative

      final String shScript = UserSecretTemplate.of(params.get("_command", String.class))
          .format(context.getSecrets());

      final Map<String, String> environments = Maps.newHashMap();
      params.getKeys()
          .forEach(key -> {
            if (CommandOperators.isValidEnvKey(key)) {
              JsonNode value = params.get(key, JsonNode.class);
              String string;
              if (value.isTextual()) {
                string = value.textValue();
              } else {
                string = value.toString();
              }
              environments.put(key, string);
            } else {
              logger.trace("Ignoring invalid env var key: {}", key);
            }
          });

      // Set up process environment according to env config. This can also refer to secrets.
      CommandOperators.collectEnvironmentVariables(environments, context.getPrivilegedVariables());

      // Write script content to runnerPath
      try (Writer writer = Files.newBufferedWriter(runnerPath)) {
        writer.write(shScript);
      }

      final CommandRequest commandRequest = buildCommandRequest(commandContext, workingDirectory,
          tempDir, environments, cmdline.build());
      return exec.run(commandContext, commandRequest);

      // TaskExecutionException could not be thrown here to poll the task by non-blocking for process-base
      // command executor. Because they will be bounded by the _instance_ where the command was executed
      // first.
    }

    private CommandContext buildCommandContext(final Path projectPath) {
      return CommandContext.builder()
          .localProjectPath(projectPath)
          .taskRequest(this.request)
          .build();
    }

    private CommandRequest buildCommandRequest(final CommandContext commandContext,
        final Path workingDirectory,
        final Path tempDir,
        final Map<String, String> environments,
        final List<String> cmdline) {
      final Path projectPath = commandContext.getLocalProjectPath();
      final Path relativeWorkingDirectory = projectPath.relativize(workingDirectory); // relative
      final Path ioDirectory = projectPath.relativize(tempDir); // relative
      return CommandRequest.builder()
          .workingDirectory(relativeWorkingDirectory)
          .environments(environments)
          .commandLine(cmdline)
          .ioDirectory(ioDirectory)
          .build();
    }
  }

  public static Object createVariableObjectFromStdout(
      String stdoutData,
      String stdoutFormat
  ) {
    // stdout is text
    if ("text".equals(stdoutFormat)) {
      return stdoutData;
    }

    // case of '*-delimited'
    String delimiter = null;
    if ("newline-delimited".equals(stdoutFormat)) {
      delimiter = "\n";
    } else if ("space-delimited".equals(stdoutFormat)) {
      delimiter = "\n| ";
    }
    if (delimiter != null) {
      List<String> listObj = new LinkedList<>();
      for (String s : stdoutData.split(delimiter)) {
        if (s.trim().length() > 0) {
          listObj.add(s.trim());
        }
      }
      return listObj;
    }

    if ("json-list-map".equals(stdoutFormat)) {
      // stdout is json format
      List<Map<String, Object>> jsonObj;
      try {
        ObjectMapper mapper = new ObjectMapper();
        jsonObj = mapper
            .readValue(stdoutData, new TypeReference<ArrayList<HashMap<String, Object>>>() {
            });
      } catch (IOException e) {
        throw Throwables.propagate(e);
      }
      return jsonObj;

    }
    return null;
  }
}
