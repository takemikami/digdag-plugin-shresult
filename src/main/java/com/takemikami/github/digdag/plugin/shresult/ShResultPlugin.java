package com.takemikami.github.digdag.plugin.shresult;

import com.google.inject.Inject;
import io.digdag.spi.CommandExecutor;
import io.digdag.spi.OperatorFactory;
import io.digdag.spi.OperatorProvider;
import io.digdag.spi.Plugin;
import java.util.Arrays;
import java.util.List;

public class ShResultPlugin implements Plugin {

  @Override
  public <T> Class<? extends T> getServiceProvider(Class<T> type) {
    if (type == OperatorProvider.class) {
      return ShResultOperatorProvider.class.asSubclass(type);
    } else {
      return null;
    }
  }

  public static class ShResultOperatorProvider implements OperatorProvider {

    @Inject
    protected CommandExecutor commandExecutor;

    @Override
    public List<OperatorFactory> get() {
      return Arrays.asList(new ShResultOperatorFactory(commandExecutor));
    }
  }
}
