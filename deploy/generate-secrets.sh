#!/usr/bin/env sh
# Creates ./secrets/ with every secret deploy/docker-compose.example.yml needs (docs/24).
# Run once, next to the Compose file:   sh generate-secrets.sh owner@example.com
# Existing files are kept, so running it again never replaces a key in use.
# Needs openssl (Linux, macOS, Git Bash or WSL on Windows).
set -eu

ADMIN_EMAIL="${1:-}"
DIR="${SECRETS_DIR:-./secrets}"
mkdir -p "$DIR"
chmod 700 "$DIR" 2>/dev/null || true

new() { # name, command, [multiline]
  if [ -s "$DIR/$1" ]; then
    echo "kept     $DIR/$1"
  else
    # No CR (openssl on Windows writes CRLF) and, for one-line values, no line end at all: the same
    # bytes must reach Postgres, Redis and SecureOne.
    if [ "${3:-}" = multiline ]; then
      sh -c "$2" | tr -d '\r' > "$DIR/$1"
    else
      sh -c "$2" | tr -d '\r\n' > "$DIR/$1"
    fi
    chmod 600 "$DIR/$1" 2>/dev/null || true
    echo "created  $DIR/$1"
  fi
}

new db_password       'openssl rand -hex 24'
new redis_password    'openssl rand -hex 24'
new redis_url         "printf 'redis://:%s@redis:6379' \"\$(cat '$DIR/redis_password')\""
new encryption_key    'printf "base64:%s" "$(openssl rand -base64 32)"'
new otp_pepper        'openssl rand -hex 32'
new jwk_private_key   'openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 2>/dev/null' multiline
# Upper and lower case, digit and symbol, whatever the random part is (password policy).
new admin_password    'printf "Sa1-%s-%s!" "$(openssl rand -hex 8)" "$(openssl rand -hex 8 | tr a-f A-F)"'

echo
echo "Back up $DIR (above all encryption_key and jwk_private_key) somewhere other than this server:"
echo "a database backup cannot decrypt stored secrets without encryption_key."
if [ -n "$ADMIN_EMAIL" ]; then
  echo
  echo "First admin: $ADMIN_EMAIL, password in $DIR/admin_password."
  echo "Start with:  ADMIN_EMAIL=$ADMIN_EMAIL PUBLIC_URL=https://your-host docker compose -f docker-compose.example.yml up -d"
fi
