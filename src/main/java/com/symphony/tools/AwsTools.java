package com.symphony.tools;

import com.symphony.tools.s3.Function;
import com.symphony.tools.s3.ReplaceAvatarWithDefaultFunction;

import java.util.LinkedList;
import java.util.List;


/**
 *
 *
 */
public class AwsTools {
  private final static List<Function> FUNCTIONS = new LinkedList<>();

  static {
    FUNCTIONS.add(new ReplaceAvatarWithDefaultFunction());
  }

  public static void main(String[] args) throws Exception {
    long start = System.nanoTime();
    AwsTools tools = new AwsTools();
    tools.start(args);
    System.out.println("time:" + ((System.nanoTime() - start) / 1000000000L) + " seconds");
  }

  public void start(String[] args) throws Exception {
    Function function = null;
    for (Function fn : FUNCTIONS) {
      if (fn.getName().equals(args[0].toLowerCase())) {
        function = fn;
      }
    }
    if (function != null) {
      System.out.println("Executing function: " + function.getName());
      function.init(args);
      function.execute();
      System.out.println("Success!");
    } else {
      printUsage();
    }
  }

  private void printUsage() {
    System.out.println("java -jar symphony-aws-tools.jar [functionName]");
    System.out.println("Available functions:");
    for (Function fn : FUNCTIONS) {
      System.out.println(fn.getName());
    }
  }


}
