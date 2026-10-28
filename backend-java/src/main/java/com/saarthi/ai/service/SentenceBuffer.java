package com.saarthi.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

/**
 * Groups a Llama token stream into complete spoken sentences. Buffers until a
 * terminator (. ! ? Devanagari danda or newline); on completion flushes any
 * remainder as the final sentence. A runaway buffer force-splits at 400 chars
 * so a pathological stream can never stall output forever. Half a sentence is
 * never emitted - robotic TTS is worse than a long clause.
 */
@Slf4j
@Service
public class SentenceBuffer {

    private static final int MAX_SENTENCE_CHARS = 400;

    public static Flux<String> toSentences(Flux<String> tokens) {
        return Flux.<String>create(sink -> {
            StringBuilder buffer = new StringBuilder();
            tokens.subscribe(
                    token -> {
                        if (token == null || sink.isCancelled()) {
                            return;
                        }
                        buffer.append(token);
                        drainSentences(buffer, sink, false);
                    },
                    sink::error,
                    () -> {
                        drainSentences(buffer, sink, true);
                        sink.complete();
                    });
        });
    }

    private static void drainSentences(StringBuilder buffer, FluxSink<String> sink, boolean flush) {
        int i = 0;
        while (i < buffer.length()) {
            if (!isSentenceEnd(buffer.charAt(i))) {
                i++;
                continue;
            }
            int end = i + 1;
            while (end < buffer.length() && buffer.charAt(end) != '\n'
                    && isSentenceEnd(buffer.charAt(end))) {
                end++;
            }
            String sentence = buffer.substring(0, end).trim();
            int rest = end;
            while (rest < buffer.length() && Character.isWhitespace(buffer.charAt(rest))) {
                rest++;
            }
            buffer.delete(0, rest);
            i = 0;
            if (!sentence.isBlank()) {
                sink.next(sentence);
            }
        }
        if (buffer.length() > MAX_SENTENCE_CHARS) {
            int space = buffer.lastIndexOf(" ", MAX_SENTENCE_CHARS);
            if (space > 0) {
                String sentence = buffer.substring(0, space).trim();
                buffer.delete(0, space + 1);
                if (!sentence.isBlank()) {
                    sink.next(sentence);
                }
            }
        }
        if (flush && !buffer.toString().isBlank()) {
            String sentence = buffer.toString().trim();
            buffer.setLength(0);
            sink.next(sentence);
        }
    }

    private static boolean isSentenceEnd(char c) {
        return c == '.' || c == '!' || c == '?' || c == '।' || c == '\n';
    }
}
