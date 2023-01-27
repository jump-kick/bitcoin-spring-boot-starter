package org.tbk.lnurl.auth;

import org.tbk.lnurl.Lnurl;

import java.util.Arrays;
import java.util.Optional;

import static java.util.Objects.requireNonNull;

public interface LnurlAuth {

    String LNURL_AUTH_TAG_KEY = "tag";
    String LNURL_AUTH_TAG_PARAM_VALUE = "login";
    String LNURL_AUTH_K1_KEY = "k1";
    String LNURL_AUTH_ACTION_KEY = "action";

    Lnurl toLnurl();

    K1 getK1();

    Optional<Action> getAction();

    enum Action {
        REGISTER("register"), // service will create a new account linked to user's linkingKey.
        LOGIN("login"), // service will login user to an existing account linked to user's linkingKey.
        LINK("link"), // service will link a user provided linkingKey to user's existing account (if account was not originally created using lnurl-auth).
        AUTH("auth"), // some stateless action which does not require logging in (or possibly even prior registration) will be granted.
        ;

        private final String value;

        Action(String value) {
            this.value = requireNonNull(value);
        }

        public String getValue() {
            return value;
        }

        public static Action parse(String value) {
            return Arrays.stream(Action.values())
                    .filter(it -> it.getValue().equalsIgnoreCase(value))
                    .findFirst()
                    .orElseThrow(() -> {
                        String errorMessage = String.format("Unknown value for lnurl-auth action: '%s'", value);
                        return new IllegalArgumentException(errorMessage);
                    });
        }
    }
}
