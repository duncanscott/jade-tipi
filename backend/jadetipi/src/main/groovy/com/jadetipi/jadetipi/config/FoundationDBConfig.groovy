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
package com.jadetipi.jadetipi.config

import com.apple.foundationdb.Database
import com.apple.foundationdb.FDB
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.ContextStartedEvent

@Configuration
@Profile("foundationdb")
@ConfigurationProperties(prefix = "foundationdb")
class FoundationDBConfig implements ApplicationListener<ContextStartedEvent>, ApplicationListener<ContextClosedEvent> {

    String clusterFile

    private FDB fdb

    @Bean
    Database foundationDb() {
        if (fdb == null) {
            fdb = FDB.selectAPIVersion(710)
        }
        return fdb.open(clusterFile)
    }

    @Override
    void onApplicationEvent(ContextStartedEvent event) {
        if (fdb == null) {
            fdb = FDB.selectAPIVersion(710)
        }
        fdb.startNetwork()
    }

    @Override
    void onApplicationEvent(ContextClosedEvent event) {
        if (fdb != null) {
            fdb.stopNetwork()
        }
    }
}
