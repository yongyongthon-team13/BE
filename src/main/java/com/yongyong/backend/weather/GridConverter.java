package com.yongyong.backend.weather;

final class GridConverter {

    record Grid(int nx, int ny) {
    }

    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT_1 = Math.toRadians(30.0);
    private static final double STANDARD_LAT_2 = Math.toRadians(60.0);
    private static final double ORIGIN_LON = Math.toRadians(126.0);
    private static final double ORIGIN_LAT = Math.toRadians(38.0);
    private static final double ORIGIN_X = 43;
    private static final double ORIGIN_Y = 136;

    private GridConverter() {
    }

    // 기상청 단기예보 가이드의 Lambert 격자 변환
    static Grid toGrid(double latitude, double longitude) {
        double re = EARTH_RADIUS_KM / GRID_KM;
        double sn = Math.log(Math.cos(STANDARD_LAT_1) / Math.cos(STANDARD_LAT_2))
                / Math.log(halfTan(STANDARD_LAT_2) / halfTan(STANDARD_LAT_1));
        double sf = Math.pow(halfTan(STANDARD_LAT_1), sn) * Math.cos(STANDARD_LAT_1) / sn;
        double ro = re * sf / Math.pow(halfTan(ORIGIN_LAT), sn);
        double ra = re * sf / Math.pow(halfTan(Math.toRadians(latitude)), sn);

        double theta = Math.toRadians(longitude) - ORIGIN_LON;
        if (theta > Math.PI) theta -= 2.0 * Math.PI;
        if (theta < -Math.PI) theta += 2.0 * Math.PI;
        theta *= sn;

        return new Grid(
                (int) Math.floor(ra * Math.sin(theta) + ORIGIN_X + 0.5),
                (int) Math.floor(ro - ra * Math.cos(theta) + ORIGIN_Y + 0.5));
    }

    private static double halfTan(double latitude) {
        return Math.tan(Math.PI * 0.25 + latitude * 0.5);
    }
}
