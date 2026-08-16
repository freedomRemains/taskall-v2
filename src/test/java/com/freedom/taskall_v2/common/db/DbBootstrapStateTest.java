package com.freedom.taskall_v2.common.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DbBootstrapStateTest {

    @Test
    void 初期値はfalseでありsetterで値を変更できること() {

        DbBootstrapState dbBootstrapState = new DbBootstrapState();

        assertThat(dbBootstrapState.isFreshlyBootstrapped()).isFalse();

        dbBootstrapState.setFreshlyBootstrapped(true);

        assertThat(dbBootstrapState.isFreshlyBootstrapped()).isTrue();
    }
}
