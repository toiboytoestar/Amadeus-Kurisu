package com.toiboytoestar.kurisu.core;

import java.io.IOException;
import java.util.List;

/** Streaming chat client. Calls are blocking: run them on a background thread. */
public interface LlmClient {

    interface Callback {
        /** A new piece of generated text. */
        void onDelta(String text);

        /** Polled between chunks; return true to abort the request. */
        boolean isCancelled();
    }

    /** Thrown for HTTP-level and API-level failures, with a message fit for display. */
    class LlmException extends IOException {
        public final int status;

        public LlmException(String message, int status) {
            super(message);
            this.status = status;
        }
    }

    void stream(String system, List<Msg> messages, Callback cb) throws IOException;
}
