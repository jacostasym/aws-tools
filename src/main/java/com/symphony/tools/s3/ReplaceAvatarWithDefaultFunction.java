package com.symphony.tools.s3;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by <a href="mailto: sushil@symphony.com">Sushil</a> on 9/12/18.
 */
public class ReplaceAvatarWithDefaultFunction implements Function {
  private static final String FUNCTION_NAME = "replace-with-default-avatar";

  private final static ByteBuffer buffer_50;
  private final static ByteBuffer buffer_150;
  private final static ByteBuffer buffer_500;
  private final static ByteBuffer buffer_600;
  private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();
  private static final LinkedBlockingQueue<Work> queue = new LinkedBlockingQueue<>(1024);
  private static final ExecutorService executer = Executors.newFixedThreadPool(THREAD_COUNT);
  private static final CountDownLatch LATCH = new CountDownLatch(THREAD_COUNT);

  static {
    try {
      buffer_50 = ByteBuffer.wrap(Files.readAllBytes(Paths.get("src/main/resources/50.png")));
      buffer_150 = ByteBuffer.wrap(Files.readAllBytes(Paths.get("src/main/resources/150.png")));
      buffer_500 = ByteBuffer.wrap(Files.readAllBytes(Paths.get("src/main/resources/500.png")));
      buffer_600 = ByteBuffer.wrap(Files.readAllBytes(Paths.get("src/main/resources/600.png")));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Argument(index = 0, required = true)
  private String function;
  @Option(name = "-accessKey", required = false, usage = "aws accessKey")
  private String accessKey;
  @Option(name = "-secretKey", required = false, usage = "aws secretKey")
  private String secretKey;
  @Option(name = "-bucket", required = true, usage = "bucket for avatar")
  private String bucket;
  @Option(name = "-prefix", required = true,
      usage = "bucket prefix to avatars. See userpic node in Zk")
  private String prefix;
  private S3Client s3;
  private AwsCredentialsProvider credentialsProvider;

  @Option(name = "-region", hidden = true,
      usage = "bucket prefix to avatars. See userpic node in Zk")
  private String region = "us-east-1";

  public void init(String args[]) throws CmdLineException {
    CmdLineParser parser = new CmdLineParser(this);
    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      System.err.println("java -jar AwsTools.jar [options] arguments...");
      throw e;
    }


    if (accessKey != null && secretKey != null) {
      credentialsProvider = StaticCredentialsProvider.create(
          AwsCredentials.create(accessKey, secretKey));
    } else {
      credentialsProvider = DefaultCredentialsProvider.create();
    }

    this.s3 = S3Client.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build();
  }



  public void replaceWithDefaultAvatarForCompanyPrefix()
      throws IOException, InterruptedException {
    System.out.println("Replacing objects in bucket: " + bucket);
    System.out.println("Replacing objects with prefix: " + prefix);
    ListObjectsV2Request listObjectsReqManual = ListObjectsV2Request.builder()
        .bucket(bucket)
        .prefix(prefix)
        .maxKeys(1000)
        .build();

    boolean done = false;
    int count = 0;

    while (!done) {
      ListObjectsV2Response listObjResponse = s3.listObjectsV2(listObjectsReqManual);
      for (S3Object content : listObjResponse.contents()) {
        //System.out.println(content.key());
        queue.put(new Work(content.key(), getBodyByFileSizePrefix(content.key())));
        count++;
      }
      if (listObjResponse.nextContinuationToken() == null) {
        done = true;
      }

      listObjectsReqManual = listObjectsReqManual.toBuilder()
          .continuationToken(listObjResponse.nextContinuationToken())
          .build();
    }
    System.out.println("Total count:" + count);
  }

  private RequestBody getBodyByFileSizePrefix(String key) {
    if (key.contains("/50/")) {
      return RequestBody.fromByteBuffer(buffer_50);
    }
    if (key.contains("/150/")) {
      return RequestBody.fromByteBuffer(buffer_150);
    }
    if (key.contains("/500/")) {
      return RequestBody.fromByteBuffer(buffer_500);
    }
    if (key.contains("/600/")) {
      return RequestBody.fromByteBuffer(buffer_600);
    }
    //return default
    return RequestBody.fromByteBuffer(buffer_50);
  }

  @Override
  public String getName() {
    return FUNCTION_NAME;
  }

  @Override
  public void execute() throws IOException, InterruptedException {
    for (int i = 0; i < THREAD_COUNT; i++) {
      // System.out.println("adding new task");
      executer.execute(new Task(bucket,
          S3Client.builder().credentialsProvider(credentialsProvider).region(Region.of(region)).build()));
    }
    this.replaceWithDefaultAvatarForCompanyPrefix();
    //Shutdown after five hours not matter what
    LATCH.await(5, TimeUnit.HOURS);
    executer.shutdownNow();
  }

  private static class Task implements Runnable {
    private final String bucket;
    private final S3Client s3;
    private boolean shutdown = false;

    public Task(String bucket, S3Client s3) {
      this.bucket = bucket;
      this.s3 = s3;
    }

    @Override
    public void run() {

      while (!shutdown) {
        Work work = null;
        try {
          work = queue.poll(10, TimeUnit.SECONDS);
          if (work == null) {
            break;
          }
          updateS3Object(bucket, work.key, work.requestBody);
        } catch (Exception e) {
          e.printStackTrace();
          shutdown = true;
        }
      }
      LATCH.countDown();
    }

    public void updateS3Object(String bucket, String objectId, RequestBody requestBody) {
      PutObjectResponse request = s3.putObject(
          PutObjectRequest.builder().bucket(bucket).key(objectId).acl(ObjectCannedACL.PUBLIC_READ)
              .build(), requestBody);
      System.out.println(request);
    }
  }


  private static class Work {
    private final String key;
    private final RequestBody requestBody;

    public Work(String key, RequestBody requestBody) {
      this.key = key;
      this.requestBody = requestBody;
    }
  }
}
