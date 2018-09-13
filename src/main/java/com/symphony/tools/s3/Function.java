package com.symphony.tools.s3;

/**
 * Created by <a href="mailto: sushil@symphony.com">Sushil</a> on 9/13/18.
 */
public interface Function {
  /**
   * Initialize the function with args
   * @param args
   * @throws Exception
   */
  void init(String args[]) throws Exception;

  /**
   * Name of the function should be in all lower case
   * @return
   */
  String getName();

  /**
   * Execute the function
   * @throws Exception
   */
  void execute() throws Exception;
}
