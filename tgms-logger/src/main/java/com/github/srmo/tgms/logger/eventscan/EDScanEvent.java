package com.github.srmo.tgms.logger.eventscan;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

public class EDScanEvent {

    private final Pattern gasPattern = Pattern.compile(".*\\bgas\\b.*", Pattern.CASE_INSENSITIVE);

    public enum Type {
        Scan, FSSAllBodiesFound
    }

    // Don't know how to make them private and work with DSLJson. I'm a DSLJson Noob :)
    public LocalDateTime timestamp;
    public Type event;
    public String BodyName;
    public String SystemName;
    public String effectiveSystemName;
    public int BodyID;
    public String PlanetClass;
    public String StarType;
    public double DistanceFromArrivalLS;
    public boolean belongsToFullScan = false;

    public boolean isGasBody() {
        return PlanetClass != null && gasPattern.matcher(PlanetClass).find();
    }

    public String getStarType() {
        return StarType;
    }

    public Type getEventType() {
        return event;
    }

    public String getEffectiveSystemName() {
        return effectiveSystemName;
    }

    public void setEffectiveSystemName(String effectiveSystemName) {
        this.effectiveSystemName = effectiveSystemName;
    }

    public int getBodyID() {
        return BodyID;
    }

    public double getDistanceFromArrival() {
        return DistanceFromArrivalLS;
    }

    public String getPlanetClass() {
        return PlanetClass;
    }


    public String getBodyName() {
        return BodyName;
    }


    public void setBelongsToFullScan(boolean belongsToFullScan) {
        this.belongsToFullScan = belongsToFullScan;
    }

    void calculateEffectiveSystemName() {
        effectiveSystemName = SystemName == null ? BodyName : SystemName;
    }

    public boolean belongsToFullScan() {
        return belongsToFullScan;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EDScanEvent that = (EDScanEvent) o;
        return BodyID == that.BodyID && event == that.event && (Objects.equals(BodyName, that.BodyName) || Objects.equals(SystemName, that.BodyName) || Objects
                .equals(BodyName, that.SystemName) || Objects.equals(SystemName, that.SystemName));
    }

    @Override
    public int hashCode() {
        return Objects.hash(event, BodyName, SystemName, BodyID);
    }

    @Override
    public String toString() {
        return "EDScanEvent{" + "timestamp=" + timestamp + ", event=" + event + ", isGasGiant=" + isGasBody() + ", BodyName='" + BodyName + '\'' + ", " + "SystemName" + "='" + SystemName + '\'' + ", effectiveSystemName='" + effectiveSystemName + '\'' + ", BodyID=" + BodyID + ", PlanetClass='" + PlanetClass + '\'' + ", StarType='" + StarType + '\'' + ", DistanceFromArrivalLS=" + DistanceFromArrivalLS + ", belongsToFullScan=" + belongsToFullScan + '}';
    }
}
