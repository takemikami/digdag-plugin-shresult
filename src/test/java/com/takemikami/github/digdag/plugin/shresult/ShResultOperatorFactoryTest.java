package com.takemikami.github.digdag.plugin.shresult;

import static junit.framework.TestCase.assertEquals;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import io.digdag.client.config.ConfigException;
import io.digdag.spi.CommandExecutor;
import io.digdag.spi.PrivilegedVariables;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class ShResultOperatorFactoryTest {

  @Inject
  protected CommandExecutor commandExecutor;

  @Test
  public void testType() throws Exception {
    ShResultOperatorFactory factory = new ShResultOperatorFactory(commandExecutor);
    assertEquals("sh_result", factory.getType());
  }

  @Test
  public void testCreateVariableObjectFromStdoutText() throws Exception {
    String stdoutData = "text message";
    String obj = (String) ShResultOperatorFactory
        .createVariableObjectFromStdout(stdoutData, "text");
    assertEquals("text message", obj);
  }

  @Test
  public void testCreateVariableObjectFromStdoutJsonListMap() throws Exception {
    String stdoutData = "[{\"id\": \"001\",\"name\": \"hoge\"},{\"id\": \"002\",\"name\": \"fuga\"}]";
    List<Map<String, Object>> obj = (List<Map<String, Object>>) ShResultOperatorFactory
        .createVariableObjectFromStdout(stdoutData, "json-list-map");
    assertEquals(2, obj.size());
    assertEquals("001", obj.get(0).get("id"));
    assertEquals("hoge", obj.get(0).get("name"));
  }

  @Test
  public void testCreateVariableObjectFromStdoutNewlineDelimited() throws Exception {
    String stdoutData = "hoge\nfuga\n";
    List<String> obj = (List<String>) ShResultOperatorFactory
        .createVariableObjectFromStdout(stdoutData, "newline-delimited");
    assertEquals(2, obj.size());
    assertEquals("hoge", obj.get(0));
  }

  @Test
  public void testCreateVariableObjectFromStdoutSpaceDelimited() throws Exception {
    String stdoutData = "hoge  fuga\nfoo bar";
    List<String> obj = (List<String>) ShResultOperatorFactory
        .createVariableObjectFromStdout(stdoutData, "space-delimited");
    System.out.println(obj);
    assertEquals(4, obj.size());
    assertEquals("hoge", obj.get(0));
    assertEquals("bar", obj.get(3));
  }

  @Test
  public void testCollectEnvironmentVariablesSuccess() throws Exception {
    Map<String, String> env = new HashMap<>();
    HashMap<String, String> map = new HashMap<>();
    map.put("key", "value");
    PrivilegedVariables variables = new TestPrivilegedVariables(map);
    ShResultOperatorFactory.collectEnvironmentVariables(env, variables);
    assertEquals("value", env.get("key"));
  }

  @Test(expected = ConfigException.class)
  public void testCollectEnvironmentVariablesExceptionInvalidKey() throws Exception {
    Map<String, String> env = new HashMap<>();
    HashMap<String, String> map = new HashMap<>();
    map.put("123", "value");
    PrivilegedVariables variables = new TestPrivilegedVariables(map);
    ShResultOperatorFactory.collectEnvironmentVariables(env, variables);
  }

  class TestPrivilegedVariables implements PrivilegedVariables {

    private Map<String, String> map;

    public TestPrivilegedVariables(Map<String, String> map) {
      this.map = map;
    }

    @Override
    public String get(String s) {
      return map.get(s);
    }

    @Override
    public Optional<String> getOptional(String s) {
      return Optional.of(get(s));
    }

    @Override
    public List<String> getKeys() {
      return new ArrayList<String>(map.keySet());
    }
  }

}
