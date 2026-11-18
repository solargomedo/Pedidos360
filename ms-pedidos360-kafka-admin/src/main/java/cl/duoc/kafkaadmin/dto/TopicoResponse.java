package cl.duoc.kafkaadmin.dto;

import java.util.List;

public record TopicoResponse(String nombre, List<Particion> particiones) {
    public record Particion(int numero, Integer lider, List<Integer> replicas, List<Integer> isr) { }
}
