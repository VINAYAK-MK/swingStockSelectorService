package com.swingStockSelector.config.utils;

public class Constants {

    private Constants() {
        // prevent instantiation
    }

    public static final String PULLBACK = "PULLBACK";

    public static final String MOMENTUM = "MOMENTUM";

    public static final String BREAKOUT = "BREAKOUT";

    public static final String MEAN_REVERSION = "MEAN_REVERSION";


    public static enum Trend {

        BULLISH,
        BEARISH,
        SIDEWAYS

    }
}