package org.igv.feature;

import org.json.JSONObject;

import java.awt.Color;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Display-only behavior attached to a region of interest. */
public class RegionDisplayRule {

    public static final Color DEFAULT_HIGHLIGHT_COLOR = new Color(255, 235, 59, 80);
    public static final Color DEFAULT_COVER_COLOR = new Color(255, 255, 255, 255);

    public enum Mode {
        NONE("None"),
        HIGHLIGHT_BACKGROUND("Highlight background"),
        COVER_FOREGROUND("Cover foreground"),
        COLLAPSE("Collapse / delete");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private boolean collapsed;
    private Color regionBackgroundColor;
    private Color regionForegroundColor;
    private int priority;
    private final Map<String, TrackRegionOverride> trackOverrides = new LinkedHashMap<>();

    public Mode getMode() {
        if (collapsed) return Mode.COLLAPSE;
        if (regionBackgroundColor != null) return Mode.HIGHLIGHT_BACKGROUND;
        if (regionForegroundColor != null) return Mode.COVER_FOREGROUND;
        return Mode.NONE;
    }

    public void setMode(Mode mode) {
        Mode selected = mode == null ? Mode.NONE : mode;
        collapsed = selected == Mode.COLLAPSE;
        if (selected == Mode.HIGHLIGHT_BACKGROUND) {
            regionBackgroundColor = DEFAULT_HIGHLIGHT_COLOR;
            regionForegroundColor = null;
        } else if (selected == Mode.COVER_FOREGROUND) {
            regionForegroundColor = DEFAULT_COVER_COLOR;
            regionBackgroundColor = null;
        } else if (selected == Mode.NONE) {
            regionBackgroundColor = null;
            regionForegroundColor = null;
        }
    }

    public Color getRegionColor() {
        return regionBackgroundColor != null ? regionBackgroundColor : regionForegroundColor;
    }

    public Color getEffectiveRegionColor() {
        Color color = getRegionColor();
        if (color != null) return color;
        return getMode() == Mode.HIGHLIGHT_BACKGROUND ? DEFAULT_HIGHLIGHT_COLOR : DEFAULT_COVER_COLOR;
    }

    public void setRegionColor(Color regionColor) {
        if (getMode() == Mode.COVER_FOREGROUND) regionForegroundColor = regionColor;
        else regionBackgroundColor = regionColor;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public Color getRegionBackgroundColor() {
        return regionBackgroundColor;
    }

    public void setRegionBackgroundColor(Color color) {
        this.regionBackgroundColor = color;
    }

    public Color getRegionForegroundColor() {
        return regionForegroundColor;
    }

    public void setRegionForegroundColor(Color color) {
        this.regionForegroundColor = color;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public Map<String, TrackRegionOverride> getTrackOverrides() {
        return Collections.unmodifiableMap(trackOverrides);
    }

    public TrackRegionOverride getTrackOverride(String trackId) {
        return trackOverrides.get(trackId);
    }

    public TrackRegionOverride getOrCreateTrackOverride(String trackId) {
        if (trackId == null || trackId.isBlank()) {
            throw new IllegalArgumentException("Track id is required for a regional override");
        }
        return trackOverrides.computeIfAbsent(trackId, ignored -> new TrackRegionOverride());
    }

    public void setTrackOverride(String trackId, TrackRegionOverride override) {
        if (override == null || !override.hasAnyEffect()) {
            trackOverrides.remove(trackId);
        } else {
            trackOverrides.put(trackId, override);
        }
    }

    public void removeTrackOverride(String trackId) {
        trackOverrides.remove(trackId);
    }

    public boolean hasAnyEffect() {
        if (collapsed || regionBackgroundColor != null || regionForegroundColor != null) return true;
        return trackOverrides.values().stream().anyMatch(TrackRegionOverride::hasAnyEffect);
    }

    public boolean hasEffectForVisibleTracks(Set<String> visibleTrackIds) {
        if (collapsed || regionBackgroundColor != null || regionForegroundColor != null) return true;
        if (visibleTrackIds == null || visibleTrackIds.isEmpty()) return false;
        for (String trackId : visibleTrackIds) {
            TrackRegionOverride override = trackOverrides.get(trackId);
            if (override != null && override.hasAnyEffect()) return true;
        }
        return false;
    }

    public RegionDisplayRule copy() {
        return fromJson(toJson());
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        if (collapsed) json.put("collapse", true);
        TrackRegionOverride.putColor(json, "backgroundColor", regionBackgroundColor);
        TrackRegionOverride.putColor(json, "foregroundColor", regionForegroundColor);
        if (priority != 0) json.put("priority", priority);
        if (!trackOverrides.isEmpty()) {
            JSONObject tracks = new JSONObject();
            for (Map.Entry<String, TrackRegionOverride> entry : trackOverrides.entrySet()) {
                if (entry.getValue().hasAnyEffect()) {
                    tracks.put(entry.getKey(), entry.getValue().toJson());
                }
            }
            if (tracks.length() > 0) json.put("tracks", tracks);
        }
        return json;
    }

    public static RegionDisplayRule fromJson(JSONObject json) {
        RegionDisplayRule rule = new RegionDisplayRule();
        if (json.has("backgroundColor") || json.has("foregroundColor") || json.has("collapse")) {
            rule.collapsed = json.optBoolean("collapse", false);
            rule.regionBackgroundColor = TrackRegionOverride.getColor(json, "backgroundColor");
            rule.regionForegroundColor = TrackRegionOverride.getColor(json, "foregroundColor");
        } else {
            String modeName = json.optString("mode", Mode.NONE.name());
            Mode legacyMode;
            try {
                legacyMode = Mode.valueOf(modeName);
            } catch (IllegalArgumentException ignored) {
                legacyMode = Mode.NONE;
            }
            Color legacyColor = TrackRegionOverride.getColor(json, "color");
            if (legacyMode == Mode.COLLAPSE) rule.collapsed = true;
            else if (legacyMode == Mode.HIGHLIGHT_BACKGROUND) {
                rule.regionBackgroundColor = legacyColor == null ? DEFAULT_HIGHLIGHT_COLOR : legacyColor;
            } else if (legacyMode == Mode.COVER_FOREGROUND) {
                rule.regionForegroundColor = legacyColor == null ? DEFAULT_COVER_COLOR : legacyColor;
            }
        }
        rule.priority = json.optInt("priority", 0);
        JSONObject tracks = json.optJSONObject("tracks");
        if (tracks != null) {
            for (String trackId : tracks.keySet()) {
                JSONObject trackJson = tracks.optJSONObject(trackId);
                if (trackJson != null) {
                    TrackRegionOverride override = TrackRegionOverride.fromJson(trackJson);
                    if (override.hasAnyEffect()) rule.trackOverrides.put(trackId, override);
                }
            }
        }
        return rule;
    }
}
