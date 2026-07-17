#!/bin/bash
set -e

create_user_and_database() {
    local user=$1
    local password=$2
    local db=$3
    echo "Creating user '$user' and database '$db'"
    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
        CREATE USER "$user" WITH PASSWORD '$password';
        CREATE DATABASE "$db" OWNER "$user";
        GRANT ALL PRIVILEGES ON DATABASE "$db" TO "$user";
EOSQL
}

create_user_and_database "auth_user"         "auth_password"         "${AUTH_DATABASE_NAME:-auth_service}"
create_user_and_database "user_user"         "user_password"         "${USER_DATABASE_NAME:-user_service}"
create_user_and_database "chat_user"         "chat_password"         "${CHAT_DATABASE_NAME:-chat_service}"
create_user_and_database "friendship_user"   "friendship_password"   "${FRIENDSHIP_DATABASE_NAME:-friendship_service}"
create_user_and_database "notification_user" "notification_password" "${NOTIFICATION_DATABASE_NAME:-notification_service}"
create_user_and_database "presence_user"     "presence_password"     "${PRESENCE_DATABASE_NAME:-presence_service}"
create_user_and_database "voice_user"        "voice_password"        "${VOICE_DATABASE_NAME:-voice_service}"
