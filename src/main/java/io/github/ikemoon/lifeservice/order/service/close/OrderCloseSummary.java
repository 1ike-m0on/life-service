package io.github.ikemoon.lifeservice.order.service.close;

public record OrderCloseSummary(int scanned, int closed, int stockReleased, int failed) {
}
