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
