/**
 * Part of Jade-Tipi — an open scientific metadata framework.
 *
 * Copyright (c) 2025 Duncan Scott and Jade-Tipi contributors
 * SPDX-License-Identifier: AGPL-3.0-only OR Commercial
 *
 * This file is part of a dual-licensed distribution:
 * - Under AGPL-3.0 for open-source use (see LICENSE)
 * - Under Commercial License for proprietary use (see DUAL-LICENSE.txt or contact licensing@jade-tipi.org)
 *
 * https://jade-tipi.org/license
 */
package org.jadetipi.id;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class IdGenerator {

    private static final int SEQ_BITS = 20;                     // up to ~1M IDs/ms per JVM
    private static final int SEQ_MASK = (1 << SEQ_BITS) - 1;

    private static final int PREFIX_LEN = 16;
    private static final int SEQ_LEN = 3;

    private static final SecureRandom RNG = createSecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    // Packs [timestamp(ms) << SEQ_BITS | seq]
    private final AtomicLong state = new AtomicLong(0L);

    /** Generate the stem (no trailing '~'). */
    public String nextId() {
        long packed = nextTimestampAndSeq();
        long ts = packed >>> SEQ_BITS;
        int  seq = (int) (packed & SEQ_MASK);

        //return randomLetters(PREFIX_LEN) + reverseDigits(ts) + toLetters(seq, SEQ_LEN);
        return randomLetters(PREFIX_LEN) + "~" + ts + "~" + toLetters(seq, SEQ_LEN);
    }

    public String nextKey() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    private static SecureRandom createSecureRandom() {
        try {
            return SecureRandom.getInstanceStrong();
        } catch (NoSuchAlgorithmException ignored) {
            return new SecureRandom();
        }
    }

    private long nextTimestampAndSeq() {
        for (;;) {
            long now = System.currentTimeMillis();
            long prev = state.get();
            long lastTs = prev >>> SEQ_BITS;
            int  lastSeq = (int) (prev & SEQ_MASK);

            long ts  = Math.max(now, lastTs);          // clamp minor clock regressions
            int  seq = (ts == lastTs) ? (lastSeq + 1) : 0;

            if (seq >= SEQ_MASK) {                     // extreme case: wait for next ms
                do { now = System.currentTimeMillis(); } while (now <= ts);
                continue;
            }

            long next = (ts << SEQ_BITS) | seq;
            if (state.compareAndSet(prev, next)) return next;
        }
    }

    private static String randomLetters(int len) {
        var r = ThreadLocalRandom.current();
        char[] c = new char[len];
        for (int i = 0; i < len; i++) c[i] = (char)('a' + r.nextInt(26));
        return new String(c);
    }

    // Reverse decimal digits of epoch ms (no fixed padding needed)
    private static String reverseDigits(long ms) {
        char[] p = Long.toString(ms).toCharArray();
        for (int i = 0, j = p.length - 1; i < j; i++, j--) {
            char t = p[i]; p[i] = p[j]; p[j] = t;
        }
        return new String(p);
    }

    private static String toLetters(int n, int width) {
        char[] c = new char[width];
        for (int i = width - 1; i >= 0; i--) { c[i] = (char)('a' + (n % 26)); n /= 26; }
        return new String(c);
    }
}
