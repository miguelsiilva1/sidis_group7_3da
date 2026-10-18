package aisafe.shared.domain;

import java.util.List;

public record PaginatedResult<T>(
        List<T> data,
        long totalElements
) {
    /**
     * One page of a list that is already complete and sorted (e.g. gathered from every replica).
     */
    public static <T> PaginatedResult<T> pageOf(List<T> all, int pageNumber, int pageSize) {
        int from = (int) Math.min((long) pageNumber * pageSize, all.size());
        int to = (int) Math.min((long) from + pageSize, all.size());
        return new PaginatedResult<>(List.copyOf(all.subList(from, to)), all.size());
    }
}
