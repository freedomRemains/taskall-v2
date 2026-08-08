#!/usr/bin/env bash
# EC2側デプロイスクリプト一式(release.sh・backup_common.sh・render-secrets-env.sh)を、
# 実行のたびにS3(ec2_scripts_prefix配下)から最新版へ更新する「自己更新」スクリプトです(issue #54)。
#
# 従来、これらのスクリプトはEC2の初回起動(cloud-init、init.sh.tftpl)時にのみS3から取得され、
# 以降はEC2ローカルの配置済みファイルがそのまま使われ続けていた(issue #39/#44)。そのため、
# terraform applyでS3上のスクリプト内容を更新しても、既に起動済みのEC2には反映されず、
# 手動でのファイル差し替えが必要になる問題があった(issue #51対応時に顕在化)。
#
# taskall-v2.service/taskall-v2-release.service/taskall-v2-backup.serviceそれぞれのExecStartPre
# として本スクリプトを呼び出すことで、各サービスの起動・実行のたびに最新版へ更新されるようにする。
# systemdユニット定義自体(*.service/*.timer)やCloudWatch Agent設定等は、変更に daemon-reload や
# タイマー再起動を伴い影響範囲が大きいため、本対応のスコープ外とする(issue #54)。
set -euo pipefail

# shellcheck source=/dev/null
source /etc/taskall-v2/config.env

readonly EC2_SCRIPTS_S3_URI="s3://${ARTIFACT_BUCKET}/${EC2_SCRIPTS_PREFIX}"

update_script() {
  local file_name="$1"
  local dest_path="$2"
  aws s3 cp "${EC2_SCRIPTS_S3_URI}/${file_name}" "${dest_path}" --region "${AWS_REGION}"
  chmod 750 "${dest_path}"
}

update_script "release.sh" /opt/taskall-v2/bin/release.sh
update_script "backup_common.sh" /opt/taskall-v2/bin/backup_common.sh
update_script "render-secrets-env.sh" /opt/taskall-v2/bin/render-secrets-env.sh
# 自分自身も更新対象に含める(Linuxではファイルを上書きしても実行中のプロセスは元のinodeを
# 参照し続けるため、本スクリプト実行中の動作には影響しない。次回実行時から新しい内容が使われる)
update_script "update-ec2-scripts.sh" /opt/taskall-v2/bin/update-ec2-scripts.sh

echo "$(date '+%Y-%m-%d %H:%M:%S') [update-ec2-scripts] EC2側スクリプトをS3から最新化しました"
