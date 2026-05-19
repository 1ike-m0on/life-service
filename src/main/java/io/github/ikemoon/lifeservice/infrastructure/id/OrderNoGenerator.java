package io.github.ikemoon.lifeservice.infrastructure.id;

public interface OrderNoGenerator {

    String nextOrderNo(String prefix);
}
