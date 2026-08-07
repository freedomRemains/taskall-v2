#!/usr/bin/env bash
# EC2側のリリーススクリプト(issue #39)。
# systemdタイマー(taskall-v2-release.timer)により5分間隔で実行される。
# S3上の最新アーティファクト(jar)のバージョンID(S3バケットのバージョニング機能を利用)を
# ポーリングし、前回デプロイ時と異なれば
#   バックアップ → アプリ停止 → 新jar配置 → アプリ起動 → ヘルスチェック
# の順でリリースを実行する。ヘルスチェックに失敗した場合は、直前にバックアップした
# 旧jarへ自動的にロールバックする。
set -u
set -o pipefail

CONFIG_FILE=/etc/taskall-v2/config.env
# shellcheck source=/etc/taskall-v2/config.env
source "${CONFIG_FILE}"

LOG_FILE="${LOG_DIR}/release.log"
LOCK_FILE=/var/run/taskall-v2-release.lock
LIB_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=./backup_common.sh
source "${LIB_DIR}/backup_common.sh"

log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') [release] $*" | tee -a "${LOG_FILE}"
}

# cronタイマーの多重実行防止(issue #39の追加提案)。systemdの同一Unit排他だけに頼らず、
# 手動実行や将来cronへ移行した場合にも安全なよう、flockで自前の排他制御を行う。
exec 9>"${LOCK_FILE}"
if ! flock -n 9; then
  log "他のリリース処理が実行中のため、今回の実行をスキップします"
  exit 0
fi

health_check() {
  local i
  for ((i = 0; i < HEALTH_CHECK_RETRIES; i++)); do
    sleep "${HEALTH_CHECK_INTERVAL_SEC}"
    if systemctl is-active --quiet "${SERVICE_NAME}" \
        && curl --silent --fail --max-time 5 "http://127.0.0.1:${APP_PORT}/" >/dev/null 2>&1; then
      return 0
    fi
  done
  return 1
}

main() {
  local latest_version_id
  latest_version_id=$(aws s3api head-object \
    --bucket "${ARTIFACT_BUCKET}" \
    --key "${ARTIFACT_OBJECT_KEY}" \
    --region "${AWS_REGION}" \
    --query 'VersionId' --output text 2>/dev/null) || {
    log "S3上のアーティファクト確認に失敗しました(未リリース、または権限/ネットワークの問題の可能性)"
    return 0
  }

  if [ -z "${latest_version_id}" ] || [ "${latest_version_id}" = "None" ]; then
    log "S3上にアーティファクトが見つかりません"
    return 0
  fi

  local deployed_version_id=""
  if [ -f "${DEPLOYED_VERSION_FILE}" ]; then
    deployed_version_id=$(cat "${DEPLOYED_VERSION_FILE}")
  fi

  if [ "${latest_version_id}" = "${deployed_version_id}" ]; then
    # 差分なし。何もしない。
    return 0
  fi

  log "新しいバージョンを検知しました(旧: ${deployed_version_id:-なし} / 新: ${latest_version_id})。リリースを開始します"

  local tmp_jar
  tmp_jar=$(mktemp /tmp/taskall-v2-XXXXXX.jar)
  if ! aws s3api get-object \
      --bucket "${ARTIFACT_BUCKET}" \
      --key "${ARTIFACT_OBJECT_KEY}" \
      --version-id "${latest_version_id}" \
      --region "${AWS_REGION}" \
      "${tmp_jar}" >/dev/null; then
    log "新バージョンのダウンロードに失敗しました。リリースを中止します"
    rm -f "${tmp_jar}"
    return 1
  fi

  # リリース直前のバックアップ(ロールバック時の退避先も兼ねる)
  local backup_dir
  backup_dir=$(backup_app "release-$(date '+%Y_%m_%d_%H_%M_%S')")
  if [ -z "${backup_dir}" ]; then
    log "バックアップに失敗しました。リリースを中止します"
    rm -f "${tmp_jar}"
    return 1
  fi

  log "アプリを停止します"
  systemctl stop "${SERVICE_NAME}"

  mv "${tmp_jar}" "${APP_JAR}"
  chmod 644 "${APP_JAR}"

  log "アプリを起動します"
  systemctl start "${SERVICE_NAME}"

  if health_check; then
    echo "${latest_version_id}" > "${DEPLOYED_VERSION_FILE}"
    log "リリースが完了しました(バージョン: ${latest_version_id})"
    prune_history
    return 0
  fi

  log "ヘルスチェックに失敗したため、旧バージョンへ自動ロールバックします(退避先: ${backup_dir})"
  systemctl stop "${SERVICE_NAME}" || true
  cp "${backup_dir}/taskall-v2.jar" "${APP_JAR}"
  systemctl start "${SERVICE_NAME}"

  if health_check; then
    log "ロールバックに成功しました。旧バージョンで稼働を継続します"
  else
    log "ロールバック後もヘルスチェックに失敗しています。手動での確認が必要です"
  fi
  return 1
}

main
exit_code=$?
exec 9>&-
exit "${exit_code}"
