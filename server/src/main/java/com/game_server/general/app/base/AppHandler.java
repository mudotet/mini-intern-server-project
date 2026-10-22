package com.game_server.general.app.base;

import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.stream.Materializer;

import java.util.concurrent.CompletionStage;

public interface AppHandler {
    CompletionStage<HttpResponse> processHttp(HttpRequest request, Materializer materializer);
}
