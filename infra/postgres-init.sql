-- One database + role per service (database-per-service)

CREATE ROLE order_user        WITH LOGIN PASSWORD 'order_pw';
CREATE ROLE inventory_user    WITH LOGIN PASSWORD 'inventory_pw';
CREATE ROLE payment_user      WITH LOGIN PASSWORD 'payment_pw';
CREATE ROLE notification_user WITH LOGIN PASSWORD 'notification_pw';

CREATE DATABASE order_db        OWNER order_user;
CREATE DATABASE inventory_db    OWNER inventory_user;
CREATE DATABASE payment_db      OWNER payment_user;
CREATE DATABASE notification_db OWNER notification_user;
