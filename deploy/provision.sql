\set ON_ERROR_STOP on
CREATE DATABASE order_inventory;
CREATE ROLE inventory_migrator LOGIN;
CREATE ROLE inventory_app LOGIN;
\password inventory_migrator
\password inventory_app
\connect order_inventory

REVOKE CONNECT, TEMPORARY ON DATABASE order_inventory FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE order_inventory TO inventory_migrator, inventory_app;
CREATE SCHEMA flyway_history AUTHORIZATION inventory_migrator;
CREATE SCHEMA inventory AUTHORIZATION inventory_migrator;
GRANT USAGE ON SCHEMA inventory TO inventory_app;

ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA inventory
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO inventory_app;
ALTER DEFAULT PRIVILEGES FOR ROLE inventory_migrator IN SCHEMA inventory
    GRANT USAGE ON SEQUENCES TO inventory_app;
