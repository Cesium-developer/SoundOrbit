package io.github.cesiumdeveloper.soundorbit;

import java.util.ArrayList;
import java.util.List;

/**
 * EQ 预设管理：保存/加载多组 10 段均衡器设置。
 * 不设数量硬上限（受设备存储限制，实际可存数千组）。
 *
 * 存储格式：每个预设 "名字|v0,v1,...,v9"，多个预设用 ';' 分隔。
 * 名字中的 '|' ';' 会被过滤，避免破坏格式。
 */
public final class EqPresets {
    public static final class Preset {
        public final String name;
        public final float[] bands;

        Preset(String name, float[] bands) {
            this.name = name;
            this.bands = bands.clone();
        }
    }

    /** 解析 Prefs 字符串；空/损坏返回空列表。 */
    public static List<Preset> parse(String raw) {
        List<Preset> out = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return out;
        String[] items = raw.split(";");
        for (String item : items) {
            if (item.isEmpty()) continue;
            int bar = item.indexOf('|');
            if (bar < 0) continue;
            String name = item.substring(0, bar);
            String[] nums = item.substring(bar + 1).split(",");
            if (nums.length != GraphicEQ.BANDS) continue;
            float[] bands = new float[GraphicEQ.BANDS];
            boolean ok = true;
            for (int i = 0; i < bands.length; i++) {
                try {
                    bands[i] = Float.parseFloat(nums[i]);
                } catch (Throwable t) { ok = false; break; }
            }
            if (ok) out.add(new Preset(name, bands));
        }
        return out;
    }

    /** 序列化为 Prefs 字符串。 */
    public static String serialize(List<Preset> presets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < presets.size(); i++) {
            if (i > 0) sb.append(';');
            Preset p = presets.get(i);
            sb.append(sanitize(p.name)).append('|');
            for (int b = 0; b < GraphicEQ.BANDS; b++) {
                if (b > 0) sb.append(',');
                sb.append(p.bands[b]);
            }
        }
        return sb.toString();
    }

    private static String sanitize(String name) {
        if (name == null) return "预设";
        return name.replace('|', '_').replace(';', '_').replace('\n', '_').trim();
    }

    private EqPresets() {
    }
}
