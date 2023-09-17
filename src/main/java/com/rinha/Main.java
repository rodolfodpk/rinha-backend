package com.rinha;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;

public class Main {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx(new VertxOptions().
      setPreferNativeTransport(true)
    );
      /*
       assuming just 1 core / event loop
       DeploymentOptions deploymentOptions = new DeploymentOptions().setInstances(1);
       vertx.deployVerticle(new MainVerticle(), deploymentOptions);
      */
    vertx.deployVerticle(new MainVerticle());
  }

}
