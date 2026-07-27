package com.freedom.taskall_v2.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * DbMngPropertiesがcustom-[環境名].yml(実行時はcustom-local.yml)の
 * 「taskall.dbmng」設定を正しくバインドできることを確認するテスト。
 *
 * 単純なプロパティバインディングの検証であるため、他クラスのような
 * Mockitoによる協働クラスのモック化ではなく、SpringBootTestで実際の
 * アプリケーションコンテキストを起動して確認する。
 */
@SpringBootTest
class DbMngPropertiesTest {

    @Autowired
    private DbMngProperties dbMngProperties;

    @Test
    void workDirがcustom_localのtaskall_dbmng_work_dirにバインドされていること() {
        assertThat(dbMngProperties.getWorkDir()).isEqualTo("./dbmng-work");
    }
}
