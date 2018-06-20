package com.takemikami.github.digdag.plugin.shresult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import io.digdag.client.config.Config;
import io.digdag.client.config.ConfigException;
import io.digdag.client.config.ConfigFactory;
import io.digdag.spi.CommandExecutor;
import io.digdag.spi.Operator;
import io.digdag.spi.OperatorContext;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.PrivilegedVariables;
import io.digdag.spi.TaskExecutionException;
import io.digdag.spi.TaskResult;
import io.digdag.util.BaseOperator;
import io.digdag.util.UserSecretTemplate;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// see https://github.com/treasure-data/digdag/blob/master/digdag-standards/src/main/java/io/digdag/standards/operator/ShOperatorFactory.java
public class ShResultOperatorFactory implements OperatorFactory {

  private static Logger logger = LoggerFactory.getLogger(ShResultOperatorFactory.class);

  private static Pattern VALID_ENV_KEY = Pattern.compile("[a-zA-Z_][a-zA-Z_0-9]*");

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

    public ShResultOperator(OperatorContext context) {
      super(context);
    }

    @Override
    public TaskResult runTask() {
      Config params = request.getConfig()
          .mergeDefault(request.getConfig().getNestedOrGetEmpty("sh"));

      List<String> shell = params.getListOrEmpty("shell", String.class);
      if (shell.isEmpty()) {
        shell = ImmutableList.of("/bin/sh");
      }
      String command = UserSecretTemplate.of(params.get("_command", String.class))
          .format(context.getSecrets());

      ProcessBuilder pb = new ProcessBuilder(shell);
      pb.directory(workspace.getPath().toFile());

      final Map<String, String> env = pb.environment();
      params.getKeys()
          .forEach(key -> {
            if (isValidEnvKey(key)) {
              JsonNode value = params.get(key, JsonNode.class);
              String string;
              if (value.isTextual()) {
                string = value.textValue();
              } else {
                string = value.toString();
              }
              env.put(key, string);
            } else {
              logger.trace("Ignoring invalid env var key: {}", key);
            }
          });

      // Set up process environment according to env config. This can also refer to secrets.
      collectEnvironmentVariables(env, context.getPrivilegedVariables());

      String stdoutData;
      int ecode;
      try {
        Process p = exec.start(workspace.getPath(), request, pb);

        // feed command to stdin
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()))) {
          writer.write(command);
        }

        ecode = p.waitFor();

        // keep stdout
        stdoutData = IOUtils.toString(p.getInputStream());

        // dump stderr
        String stderrData = IOUtils.toString(p.getErrorStream());
        logger.info(stderrData);

      } catch (IOException | InterruptedException ex) {
        throw Throwables.propagate(ex);
      }

      if (ecode != 0) {
        throw new TaskExecutionException("Command failed with code " + ecode);
      }

      String varName = params.get("destination_variable", String.class);
      String stdoutFormat = params.get("stdout_format", String.class);

      ConfigFactory cf = request.getConfig().getFactory();
      Config storeParams = cf.create();

      storeParams.set(varName, createVariableObjectFromStdout(stdoutData, stdoutFormat));

      return TaskResult.defaultBuilder(request)
          .storeParams(storeParams)
          .build();
    }
  }

  public static void collectEnvironmentVariables(Map<String, String> env,
      PrivilegedVariables variables) {
    for (String name : variables.getKeys()) {
      if (!VALID_ENV_KEY.matcher(name).matches()) {
        throw new ConfigException("Invalid _env key name: " + name);
      }
      env.put(name, variables.get(name));
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

  private static boolean isValidEnvKey(String key) {
    return VALID_ENV_KEY.matcher(key).matches();
  }

}
