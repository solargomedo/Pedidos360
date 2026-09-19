package cl.duoc.pedidos360.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import cl.duoc.pedidos360.entity.Producto;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
}
