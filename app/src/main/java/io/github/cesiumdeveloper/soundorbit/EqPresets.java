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

    /**
     * 内置 EQ 预设：10 段 ISO 倍频程（31.5/63/125/250/500/1k/2k/4k/8k/16k Hz）。
     * 数值来自公开的 10 段图示 EQ 场景曲线与 ASMR 音频制作惯例（行业常见起点），
     * 不是任何标准组织的认证值——仅作为可修改的起点。
     * nameKey 用于资源本地化，见 SettingsActivity 的映射。
     */
    public static final class Builtin {
        public final String nameKey;
        public final float[] bands;
        Builtin(String key, float[] b) { nameKey = key; bands = b; }
    }

    public static java.util.List<Builtin> builtins() {
        java.util.List<Builtin> out = new ArrayList<>();
        // 顺序：均衡/低音/高音/人声/播客/流行/摇滚/嘻哈/古典/响度/ASMR耳语/ASMR敲击
        out.add(new Builtin("flat",      f(0,0,0,0,0,0,0,0,0,0)));
        out.add(new Builtin("bass",      f(8,6,4,0,0,0,0,0,0,0)));
        out.add(new Builtin("treble",    f(0,0,0,0,0,0,0,3,5,6)));
        out.add(new Builtin("vocal",     f(0,0,0,-3,0,0,3,4,0,0)));
        out.add(new Builtin("podcast",   f(-6,-3,0,-2,0,0,0,3,0,0)));
        out.add(new Builtin("pop",       f(4,4,0,0,0,0,0,2,2,0)));
        out.add(new Builtin("rock",      f(5,5,0,0,0,0,0,3,4,0)));
        out.add(new Builtin("hiphop",    f(7,5,0,0,-2,0,0,0,3,0)));
        out.add(new Builtin("classical", f(0,2,0,1,1,0,0,0,2,0)));
        out.add(new Builtin("loudness",  f(4,3,0,0,0,0,0,3,4,5)));
        // ASMR：HPF 切低频 + 去浑浊 + presence + 空气感；6-9kHz(8k段)谨慎不提升
        out.add(new Builtin("asmr_whisper", f(-6,-3,0,-2,-1,0,3,2,0,2)));
        out.add(new Builtin("asmr_tapping", f(-6,-3,0,-3,-2,0,2,1,-1,3)));
        return out;
    }

    private static float[] f(float... v) { return v; }

    private EqPresets() {
    }
}
