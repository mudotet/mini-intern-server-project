package com.game.server.application;

import com.game.server.domain.Account;
import com.game.server.domain.Device;
import com.game.server.domain.LoginRecord;
import com.game.server.domain.Player;
import com.game.server.domain.Session;
import com.game.server.security.TokenService;
import com.game.server.store.AccountStore;
import com.game.server.store.DeviceStore;
import com.game.server.store.IdempotencyStore;
import com.game.server.store.PlayerStore;
import com.game.server.store.SessionStore;
import java.time.Clock;
import java.util.UUID;
import java.util.function.Supplier;

public final class LoginService {
    private final AccountStore accounts;
    private final DeviceStore devices;
    private final PlayerStore players;
    private final SessionStore sessions;
    private final IdempotencyStore idempotency;
    private final TokenService tokens;
    private final Clock clock;
    private final Supplier<String> ids;

    public LoginService(AccountStore accounts, DeviceStore devices, PlayerStore players, SessionStore sessions, IdempotencyStore idempotency, TokenService tokens, Clock clock, Supplier<String> ids) {
        this.accounts = accounts;
        this.devices = devices;
        this.players = players;
        this.sessions = sessions;
        this.idempotency = idempotency;
        this.tokens = tokens;
        this.clock = clock;
        this.ids = ids;
    }

    public static Supplier<String> uuidIds() { return () -> UUID.randomUUID().toString(); }

    public synchronized LoginResult login(LoginInput input) {
        validate(input);
        LoginRecord existingResult = idempotency.find(input.requestId()).orElse(null);
        if (existingResult != null) {
            if (!existingResult.matchesInput(input.deviceId(), input.accountId(), input.accountIdPresent(), input.platform(), input.identifier(), input.clientVersion())) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
            return result(existingResult);
        }

        Device device = devices.find(input.deviceId()).orElse(null);
        boolean newAccount = false;
        Account account;
        if (device != null) {
            if (!device.platform().equals(input.platform()) || !device.identifier().equals(input.identifier())) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
            if (input.accountIdPresent() && !device.accountId().equals(input.accountId())) throw new ApiException(ApiErrorCode.ACCOUNT_ID_MISMATCH);
            account = accounts.find(device.accountId()).orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_ID_MISMATCH));
        } else if (input.accountIdPresent()) {
            account = accounts.find(input.accountId()).orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_ID_MISMATCH));
            device = new Device(input.deviceId(), account.id(), input.platform(), input.identifier());
            devices.save(device);
        } else {
            String accountId = ids.get();
            String playerId = ids.get();
            account = new Account(accountId, playerId);
            accounts.save(account);
            players.save(new Player(playerId));
            device = new Device(input.deviceId(), accountId, input.platform(), input.identifier());
            devices.save(device);
            newAccount = true;
        }

        long expiresAt = clock.instant().getEpochSecond() + 3600;
        Session session = new Session(ids.get(), account.id(), account.playerId(), device.id(), expiresAt);
        String token = tokens.issue(session);
        sessions.replace(session);
        LoginRecord record = new LoginRecord(input.deviceId(), input.accountId(), input.accountIdPresent(), input.platform(), input.identifier(), input.clientVersion(), account.id(), account.playerId(), device.id(), token, expiresAt, newAccount);
        idempotency.save(input.requestId(), record);
        return result(record);
    }

    private static LoginResult result(LoginRecord value) { return new LoginResult(value.accountId(), value.playerId(), value.deviceId(), value.accessToken(), value.expiresAt(), value.newAccount()); }

    private static void validate(LoginInput input) {
        if (input == null || blank(input.requestId()) || blank(input.deviceId()) || blank(input.platform()) || blank(input.identifier()) || blank(input.clientVersion()) || input.accountIdPresent() && blank(input.accountId())) throw new ApiException(ApiErrorCode.INVALID_REQUEST);
    }

    private static boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
