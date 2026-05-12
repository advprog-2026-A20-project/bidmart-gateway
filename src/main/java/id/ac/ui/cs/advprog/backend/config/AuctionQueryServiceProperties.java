package id.ac.ui.cs.advprog.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "microservices.auction-query")
public class AuctionQueryServiceProperties {

    public enum RolloutMode {
        DISABLED,
        SHADOW,
        PERCENT,
        FULL
    }

    private boolean enabled = false;
    private String baseUrl = "";
    private int connectTimeoutMs = 500;
    private int readTimeoutMs = 1500;
    private boolean failOpen = true;
    private RolloutMode rolloutMode = RolloutMode.DISABLED;
    private int rolloutPercent = 0;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public boolean isFailOpen() {
        return failOpen;
    }

    public void setFailOpen(boolean failOpen) {
        this.failOpen = failOpen;
    }

    public RolloutMode getRolloutMode() {
        return rolloutMode;
    }

    public void setRolloutMode(RolloutMode rolloutMode) {
        this.rolloutMode = rolloutMode;
    }

    public int getRolloutPercent() {
        return rolloutPercent;
    }

    public void setRolloutPercent(int rolloutPercent) {
        this.rolloutPercent = rolloutPercent;
    }
}
