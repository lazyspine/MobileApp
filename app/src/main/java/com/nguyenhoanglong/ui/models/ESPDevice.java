package com.nguyenhoanglong.ui.models;

public class ESPDevice {
    private String deviceId;
    private String name;
    private String commandTopic;
    private boolean isLightOn;
    private boolean isRGBMode;

    public ESPDevice(String deviceId, String commandTopic) {
        this.deviceId = deviceId;
        this.name = deviceId;
        this.commandTopic = commandTopic;
        this.isLightOn = false;
        this.isRGBMode = false;
    }

    public ESPDevice(String deviceId, String name, String commandTopic, boolean isLightOn, boolean isRGBMode){
        this.deviceId = deviceId;
        this.name = deviceId;
        this.commandTopic = commandTopic;
        this.isLightOn = isLightOn;
        this.isRGBMode = isRGBMode;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCommandTopic() {
        return commandTopic;
    }

    public boolean isLightOn() {
        return isLightOn;
    }

    public void setLightOn(boolean lightOn) {
        this.isLightOn = lightOn;
    }

    public boolean isRGBMode() {
        return isRGBMode;
    }

    public void setRGBMode(boolean isRGBMode) {
        this.isRGBMode = isRGBMode;
    }
}