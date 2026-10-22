package com.game_server.general.app.handler.common;

import com.game.server.proto.CommonInitContract;
import com.game_server.general.app.base.ApiHandler;
import com.game_server.general.app.base.BaseApiHandler;
import com.game_server.general.app.handler.ApiCodes;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import java.time.Instant;

@ApiHandler(value = ApiCodes.COMMON_INIT, auth = false)
public final class InitHandler extends BaseApiHandler {
    @Override
    protected Message process(byte[] body) throws InvalidProtocolBufferException {
        CommonInitContract.CommonInitRequest.parseFrom(body);
        return CommonInitContract.CommonInitResponseProto.newBuilder()
                .setServerTime(Instant.now().getEpochSecond())
                .setContractVersion("1")
                .setConfigurationVersion("1")
                .setMinimumClientVersion("1.0.0")
                .build();
    }
}
