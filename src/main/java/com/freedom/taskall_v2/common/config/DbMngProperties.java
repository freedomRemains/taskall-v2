package com.freedom.taskall_v2.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * DBメンテナンス機能(DB構成取得/DB構成更新)のアプリ独自設定を保持するクラス。
 *
 * SpringBoot自体の設定(application-[環境名].yml)ではなく、アプリ独自の設定
 * (custom-[環境名].yml)の「taskall.dbmng」配下の値をバインドする。
 * DB構成取得で生成するTSV/SQLファイルの配置先は、環境ごとに変わり得るため、
 * クラスパス直下(src/main/resources)ではなく実行時に指定するこの作業ディレクトリを使用する。
 */
@Component
@ConfigurationProperties(prefix = "taskall.dbmng")
public class DbMngProperties {

    /** DB構成取得で生成するTSV/SQLファイルの配置先ディレクトリ */
    private String workDir;

    public String getWorkDir() {
        return workDir;
    }

    public void setWorkDir(String workDir) {
        this.workDir = workDir;
    }
}
