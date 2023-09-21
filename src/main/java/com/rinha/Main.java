package com.rinha;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(vertxOptions());
      /*
       assuming just 1 core / event loop
       DeploymentOptions deploymentOptions = new DeploymentOptions().setInstances(1);
       vertx.deployVerticle(new MainVerticle(), deploymentOptions);
      */
    vertx.deployVerticle(new MainVerticle());
  }

  private static VertxOptions vertxOptions() {
    return new VertxOptions()
            .setPreferNativeTransport(true)
            // .setEventLoopPoolSize(1); // the default is 2 * Runtime.getRuntime().availableProcessors()
            ;
  }

  private static DeploymentOptions deploymentOptions() {
    return new DeploymentOptions().setInstances(1);
  }


}
