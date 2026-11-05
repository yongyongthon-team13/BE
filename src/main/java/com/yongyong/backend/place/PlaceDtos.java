package com.yongyong.backend.place;

import java.util.List;

public final class PlaceDtos {
    private PlaceDtos() {}

    public record Location(double latitude, double longitude) {}

    public record Place(String placeId, String name, String address, String category,
                        Location location) {}

    public record SearchResult(int total, List<Place> places) {}
}
