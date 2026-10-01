package com.game_server.general.app.base;

import akka.http.javadsl.model.ContentType;
import akka.http.javadsl.model.ContentTypes;
import akka.http.javadsl.model.HttpEntities;
import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.StatusCode;
import akka.http.javadsl.model.StatusCodes;
import com.google.protobuf.Message;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import java.util.Collections;
import java.util.Objects;

public final class ApiResult {
    public static final ContentType CONTENT_TYPE =
            ContentTypes.create(MediaTypes.applicationBinary("x-protobuf", false));

    private final StatusCode statusCode;
    private final Message data;
    private final Iterable<HttpHeader> headers;

    private ApiResult(StatusCode statusCode, Message data, Iterable<HttpHeader> headers) {
        this.statusCode = Objects.requireNonNull(statusCode, "statusCode");
        this.data = data;
        this.headers = headers;
    }

    public static ApiResult success() {
        return response(StatusCodes.OK);
    }

    public static ApiResult success(Message data) {
        return response(StatusCodes.OK, data);
    }

    public static ApiResult response(StatusCode statusCode) {
        return new ApiResult(statusCode, null, Collections.emptyList());
    }

    public static ApiResult response(StatusCode statusCode, Message data) {
        return new ApiResult(statusCode, Objects.requireNonNull(data, "data"), Collections.emptyList());
    }

    public static ApiResult response(int code) {
        return response(StatusCodes.custom(code, "", ""));
    }

    public static ApiResult response(int code, Message data) {
        return response(StatusCodes.custom(code, "", ""), data);
    }

    public StatusCode getStatusCode() {
        return statusCode;
    }

    public ApiResult withHeaders(Iterable<HttpHeader> headers) {
        return new ApiResult(statusCode, data, Objects.requireNonNull(headers, "headers"));
    }

    public HttpResponse toResponse(String requestId) {
        return toResponse(requestId, false);
    }

    public HttpResponse toResponse(String requestId, boolean json) {
        HttpResponse response = HttpResponse.create().withStatus(statusCode)
                .addHeader(HttpHeader.parse("Cache-Control", "no-store"));
        if (requestId != null && !requestId.isBlank()) {
            response = response.addHeader(HttpHeader.parse(BaseApiHandler.HEADER_ID, requestId));
        }
        if (data != null) {
            if (json) {
                try {
                    response = response.withEntity(HttpEntities.create(ContentTypes.APPLICATION_JSON,
                            JsonFormat.printer().preservingProtoFieldNames().includingDefaultValueFields().print(data)));
                } catch (InvalidProtocolBufferException invalid) {
                    throw new IllegalStateException("Cannot serialize response", invalid);
                }
            } else {
                response = response.withEntity(HttpEntities.create(CONTENT_TYPE, data.toByteArray()));
            }
        }
        return response.addHeaders(headers);
    }
}
