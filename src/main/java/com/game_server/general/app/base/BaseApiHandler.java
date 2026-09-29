package com.game_server.general.app.base;

import akka.http.javadsl.model.HttpResponse;
import akka.http.javadsl.model.StatusCodes;
import com.game.server.proto.ErrorContract;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

public abstract class BaseApiHandler {
    public static final String HEADER_ID = "X-Request-Id";

    public final HttpResponse processHttp(byte[] body) {
        try {
            return ApiResult.success(process(body)).toResponse(null);
        } catch (InvalidProtocolBufferException | IllegalArgumentException exception) {
            return ApiResult.response(StatusCodes.BAD_REQUEST,
                    ErrorContract.AuthErrorProto.newBuilder().setMessage("Invalid login request").build()).toResponse(null);
        } catch (IllegalStateException exception) {
            return ApiResult.response(StatusCodes.CONFLICT,
                    ErrorContract.AuthErrorProto.newBuilder().setMessage("Device or player identity does not match").build()).toResponse(null);
        }
    }

    protected abstract Message process(byte[] body) throws InvalidProtocolBufferException;
}
