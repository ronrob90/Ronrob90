package com.ronrob.brti.market;

/** Expected validation failure whose state and explanation may be shown to a client. */
public class MarketUnavailableException extends Exception {

    private final String state;

    public MarketUnavailableException(String state, String message) {
        super(message);
        this.state = state;
    }

    public String state() {
        return state;
    }
}
