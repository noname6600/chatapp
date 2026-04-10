#!/bin/bash
# Creates isolated PostgreSQL roles and databases per service.

set -e
set -u

function create_role_and_database() {
    local database=$1
    local role=$2
    local password=$3

    echo "Ensuring role '$role' and database '$database' exist"

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
DO
\$\$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '$role') THEN
        CREATE ROLE "$role" LOGIN PASSWORD '$password';
    ELSE
        ALTER ROLE "$role" LOGIN PASSWORD '$password';
    END IF;
END
\$\$;
EOSQL

    if [ "$(psql -tA --username "$POSTGRES_USER" --dbname postgres -c "SELECT 1 FROM pg_database WHERE datname = '$database';")" != "1" ]; then
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres -c "CREATE DATABASE \"$database\" OWNER \"$role\";"
    fi

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname postgres <<-EOSQL
REVOKE ALL ON DATABASE "$database" FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE "$database" TO "$role";
EOSQL

    psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$database" <<-EOSQL
ALTER SCHEMA public OWNER TO "$role";
GRANT ALL ON SCHEMA public TO "$role";
EOSQL
}

create_role_and_database "${AUTH_DATABASE_NAME:-auth_service}" "${AUTH_DATABASE_USER:-auth_user}" "${AUTH_DATABASE_PASSWORD:-auth_password}"
create_role_and_database "${USER_DATABASE_NAME:-user_service}" "${USER_DATABASE_USER:-user_user}" "${USER_DATABASE_PASSWORD:-user_password}"
create_role_and_database "${CHAT_DATABASE_NAME:-chat_service}" "${CHAT_DATABASE_USER:-chat_user}" "${CHAT_DATABASE_PASSWORD:-chat_password}"
create_role_and_database "${FRIENDSHIP_DATABASE_NAME:-friendship_service}" "${FRIENDSHIP_DATABASE_USER:-friendship_user}" "${FRIENDSHIP_DATABASE_PASSWORD:-friendship_password}"
create_role_and_database "${NOTIFICATION_DATABASE_NAME:-notification_service}" "${NOTIFICATION_DATABASE_USER:-notification_user}" "${NOTIFICATION_DATABASE_PASSWORD:-notification_password}"

echo "Service database isolation bootstrap complete."
