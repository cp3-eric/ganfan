package com.ganfan.backend.feature.nearby;

/**
 * 坐标系转换工具。
 *
 * <p>国内地图服务商（高德、腾讯）对外返回的坐标为 GCJ-02（火星坐标），
 * 而浏览器 Geolocation API 与本项目本地种子数据（源自 OpenStreetMap）均为 WGS-84。
 * 两者在中国境内存在约 300-600 米的系统性偏移，若直接混用会导致距离计算失真。
 *
 * <p>约定：所有 VenueProvider 的入参与出参统一使用 WGS-84，
 * 仅在与地图服务端交互的边界处做转换。
 */
public final class GeoCoordinates {
    /** 克拉索夫斯基椭球长半轴（米）。 */
    private static final double AXIS = 6378245.0;
    /** 克拉索夫斯基椭球第一偏心率平方。 */
    private static final double ECCENTRICITY_SQUARED = 0.00669342162296594323;
    /** 中国境外粗略判定边界（纬度）。 */
    private static final double CHINA_MIN_LAT = 0.8293;
    private static final double CHINA_MAX_LAT = 55.8271;
    private static final double CHINA_MIN_LNG = 72.004;
    private static final double CHINA_MAX_LNG = 137.8347;

    private GeoCoordinates() {}

    /**
     * WGS-84 转 GCJ-02。境外坐标原样返回。
     */
    public static double[] wgs84ToGcj02(double lat, double lng) {
        if (!inChina(lat, lng)) return new double[] {lat, lng};
        double[] delta = offset(lat, lng);
        return new double[] {lat + delta[0], lng + delta[1]};
    }

    /**
     * GCJ-02 转 WGS-84。采用迭代反解，精度优于 1 米。境外坐标原样返回。
     */
    public static double[] gcj02ToWgs84(double lat, double lng) {
        if (!inChina(lat, lng)) return new double[] {lat, lng};
        double guessLat = lat;
        double guessLng = lng;
        for (int i = 0; i < 30; i++) {
            double[] shifted = wgs84ToGcj02(guessLat, guessLng);
            double deltaLat = shifted[0] - lat;
            double deltaLng = shifted[1] - lng;
            guessLat -= deltaLat;
            guessLng -= deltaLng;
            if (Math.abs(deltaLat) < 1e-9 && Math.abs(deltaLng) < 1e-9) break;
        }
        return new double[] {guessLat, guessLng};
    }

    /** 判断坐标是否落在中国境外（境外不适用火星坐标偏移算法）。 */
    public static boolean inChina(double lat, double lng) {
        return lat >= CHINA_MIN_LAT && lat <= CHINA_MAX_LAT
                && lng >= CHINA_MIN_LNG && lng <= CHINA_MAX_LNG;
    }

    /** 计算 GCJ-02 相对 WGS-84 的经纬度偏移量。 */
    private static double[] offset(double lat, double lng) {
        double dLat = transformLat(lng - 105.0, lat - 35.0);
        double dLng = transformLng(lng - 105.0, lat - 35.0);
        double radLat = Math.toRadians(lat);
        double magic = Math.sin(radLat);
        magic = 1 - ECCENTRICITY_SQUARED * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / (((AXIS * (1 - ECCENTRICITY_SQUARED)) / (magic * sqrtMagic)) * Math.PI);
        dLng = (dLng * 180.0) / ((AXIS / sqrtMagic) * Math.cos(radLat) * Math.PI);
        return new double[] {dLat, dLng};
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320.0 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLng(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }
}
