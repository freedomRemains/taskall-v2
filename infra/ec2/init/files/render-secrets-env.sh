#!/usr/bin/env bash
# メール送信(SMTP)接続情報を、AWS SSM Parameter Storeから取得しEC2ローカルの環境変数
# ファイルへ書き出すスクリプト(issue #41)。
#
# SpringBootのメール設定(application-prod.yaml)はコンテキスト起動時にTASKALL_MAIL_*環境変数を
# 必要とするため、taskall-v2.serviceのExecStartPre(=アプリ起動前に必ず1回実行される)から
# 呼び出す。ここで生成する/etc/taskall-v2/secrets.envはEnvironmentFileとしてのみ参照され、
# GitはもちろんS3等の永続化領域にも一切アップロードしない(パーミッション600で本ファイル自体の
# 閲覧もroot/アプリ実行ユーザに限定する)。
#
# アカウントパスワード(issue #41本体)とは異なり、メール接続情報はSpringBootのコンテキスト起動
# より前に環境変数として存在している必要があるため、Java起動処理(ApplicationRunner)ではなく
# このEC2側の起動前スクリプトでSSMから取得する方式を採る。
set -euo pipefail

# shellcheck source=/dev/null
source /etc/taskall-v2/config.env

MAIL_SSM_PREFIX="/${PROJECT_NAME}/mail"
RECAPTCHA_SSM_PREFIX="/${PROJECT_NAME}/recaptcha"
SECRETS_FILE="/etc/taskall-v2/secrets.env"

fetch_param() {
    local prefix="$1"
    local name="$2"
    aws ssm get-parameter --region "${AWS_REGION}" --with-decryption \
        --name "${prefix}/${name}" --query 'Parameter.Value' --output text
}

# SSMパラメータが1つでも未設定の場合は、メール送信不能なままの起動を防ぐため即座に失敗させる
mail_host="$(fetch_param "${MAIL_SSM_PREFIX}" host)"
mail_port="$(fetch_param "${MAIL_SSM_PREFIX}" port)"
mail_username="$(fetch_param "${MAIL_SSM_PREFIX}" username)"
mail_password="$(fetch_param "${MAIL_SSM_PREFIX}" password)"

# issue #80: reCAPTCHAキーも、メール接続情報と同じくSSM Parameter Storeから取得して注入する
recaptcha_site_key="$(fetch_param "${RECAPTCHA_SSM_PREFIX}" site-key)"
recaptcha_secret_key="$(fetch_param "${RECAPTCHA_SSM_PREFIX}" secret-key)"

umask 177
cat <<SECRETS_EOF > "${SECRETS_FILE}"
TASKALL_MAIL_HOST=${mail_host}
TASKALL_MAIL_PORT=${mail_port}
TASKALL_MAIL_USERNAME=${mail_username}
TASKALL_MAIL_PASSWORD=${mail_password}
TASKALL_RECAPTCHA_SITE_KEY=${recaptcha_site_key}
TASKALL_RECAPTCHA_SECRET_KEY=${recaptcha_secret_key}
SECRETS_EOF
chmod 600 "${SECRETS_FILE}"

echo "$(date '+%Y-%m-%d %H:%M:%S') [render-secrets-env] SSM Parameter Storeからメール接続情報・reCAPTCHAキーを取得しました"
