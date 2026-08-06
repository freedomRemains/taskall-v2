#!/usr/bin/env bash
# EC2側のバックアップ共通処理(issue #39)。
# release.sh(リリース直前バックアップ)・taskall-v2-backup.timer(毎日3時の定期バックアップ)の
# 双方から共通利用する。SQLiteのオンラインバックアップAPI(`.backup`)を使うことで、
# アプリを停止せずに安全なバックアップを取得できるため、毎日の定期バックアップではアプリ停止・
# EC2再起動は行わない(issue #39でのフィードバックにより、定期再起動は見送りとなった)。
set -u
set -o pipefail

CONFIG_FILE=/etc/taskall-v2/config.env
if [ -z "${ARTIFACT_BUCKET:-}" ]; then
  # shellcheck source=/etc/taskall-v2/config.env
  source "${CONFIG_FILE}"
fi

BACKUP_LOG_FILE="${LOG_DIR}/backup.log"

backup_log() {
  echo "$(date '+%Y-%m-%d %H:%M:%S') [backup] $*" | tee -a "${BACKUP_LOG_FILE}"
}

# 現在のjar・DBを、ローカル履歴ディレクトリとバックアップ用S3バケットの両方に保存する。
# 引数: バックアップ先ディレクトリ名(タイムスタンプ等)。省略時は現在日時から自動生成する。
# 成功時、作成したバックアップディレクトリのパスを標準出力に出す。
backup_app() {
  local label="${1:-$(date '+%Y_%m_%d_%H_%M_%S')}"
  local dest_dir="${HISTORY_DIR}/${label}"
  mkdir -p "${dest_dir}"

  if [ -f "${APP_JAR}" ]; then
    cp "${APP_JAR}" "${dest_dir}/taskall-v2.jar"
  fi

  if [ -f "${APP_DB}" ]; then
    # SQLiteのオンラインバックアップAPIを使用し、アプリ稼働中でも安全にバックアップを取得する
    # (ファイルコピーだと書き込み中の破損コピーになるリスクがあるため使用しない)
    if ! sqlite3 "${APP_DB}" ".backup '${dest_dir}/taskall-v2.db'"; then
      backup_log "DBバックアップに失敗しました(${dest_dir})"
      return 1
    fi
  fi

  if ! aws s3 cp "${dest_dir}" "s3://${BACKUP_BUCKET}/history/${label}/" --recursive --region "${AWS_REGION}" >/dev/null; then
    backup_log "S3へのバックアップアップロードに失敗しました(${dest_dir})"
    return 1
  fi

  backup_log "バックアップを作成しました: ${dest_dir} (S3: s3://${BACKUP_BUCKET}/history/${label}/)"
  echo "${dest_dir}"
}

# ローカル履歴ディレクトリの世代数を制限し、EC2ローカルディスクの圧迫を防ぐ
# (S3側はバケットのライフサイクル設定(経過日数基準)で長期保持するため、ローカルは
# 直近分のみ保持すれば十分という判断)
prune_history() {
  local keep="${HISTORY_RETENTION_COUNT:-10}"
  # shellcheck disable=SC2012
  ls -1dt "${HISTORY_DIR}"/*/ 2>/dev/null | tail -n +"$((keep + 1))" | xargs -r rm -rf
}

# 直接実行された場合(毎日3時の定期バックアップ用systemdサービスから呼ばれる想定)。
# release.shからはsourceされるだけで、この分岐には入らない。
if [ "${BASH_SOURCE[0]}" = "${0}" ]; then
  case "${1:-}" in
    daily)
      backup_app "daily-$(date '+%Y_%m_%d_%H_%M_%S')" > /dev/null
      prune_history
      ;;
    *)
      echo "使用方法: $0 daily" >&2
      exit 1
      ;;
  esac
fi
