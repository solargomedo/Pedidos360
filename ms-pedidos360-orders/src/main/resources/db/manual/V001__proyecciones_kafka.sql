CREATE TABLE IF NOT EXISTS pedido_kafka_seguimiento (
    pedido_id BIGINT PRIMARY KEY CHECK (pedido_id > 0),
    producto_id BIGINT NOT NULL CHECK (producto_id > 0),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    estado VARCHAR(32) NOT NULL,
    tipo_evento VARCHAR(32) NOT NULL,
    kafka_particion INTEGER NOT NULL CHECK (kafka_particion >= 0),
    kafka_offset BIGINT NOT NULL CHECK (kafka_offset >= 0)
);

CREATE TABLE IF NOT EXISTS pedido_kafka_analitica (
    pedido_id BIGINT PRIMARY KEY CHECK (pedido_id > 0),
    producto_id BIGINT NOT NULL CHECK (producto_id > 0),
    cantidad INTEGER NOT NULL CHECK (cantidad > 0),
    estado VARCHAR(32) NOT NULL,
    tipo_evento VARCHAR(32) NOT NULL,
    kafka_particion INTEGER NOT NULL CHECK (kafka_particion >= 0),
    kafka_offset BIGINT NOT NULL CHECK (kafka_offset >= 0)
);
