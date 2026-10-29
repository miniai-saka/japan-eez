package io.github.miniai_saka.eez;

public enum Layer {
    MARKERS("markers", "基準点・鋲"), BASELINE("baseline", "基準線・海岸線"), TERRITORY("territory", "領有境界"),
    TERRITORIAL("territorial", "領海 12ブロック"), EEZ("eez", "EEZ 200ブロック"), SHELF("shelf", "大陸棚・延長 350ブロック"), AIRSPACE("airspace", "領空");
    public final String key, label;
    Layer(String key, String label) { this.key = key; this.label = label; }
}
