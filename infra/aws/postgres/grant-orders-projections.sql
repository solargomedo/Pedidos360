-- psql variables: orders_schema and orders_role. Run with the projection DDL
-- in the same transaction, using an account that owns the target objects.
GRANT USAGE ON SCHEMA :"orders_schema" TO :"orders_role";
GRANT SELECT, INSERT, UPDATE
    ON TABLE :"orders_schema".pedido_kafka_seguimiento, :"orders_schema".pedido_kafka_analitica
    TO :"orders_role";
