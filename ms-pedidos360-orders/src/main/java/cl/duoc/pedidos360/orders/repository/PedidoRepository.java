package cl.duoc.pedidos360.orders.repository;

import cl.duoc.pedidos360.orders.entity.Pedido;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {
}
