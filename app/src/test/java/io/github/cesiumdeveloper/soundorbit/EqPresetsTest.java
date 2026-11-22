package io.github.cesiumdeveloper.soundorbit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** EQ 预设序列化/解析：往返一致、多组、名字净化、损坏数据容错。 */
public class EqPresetsTest {

    private static float[] bands(float v) {
        float[] b = new float[GraphicEQ.BANDS];
        for (int i = 0; i < b.length; i++) b[i] = v + i;
        return b;
    }

    @Test
    public void roundTrip_single() {
        List<EqPresets.Preset> list = new ArrayList<>();
        list.add(new EqPresets.Preset("我的调音", bands(0f)));
        String raw = EqPresets.serialize(list);
        List<EqPresets.Preset> back = EqPresets.parse(raw);
        assertEquals(1, back.size());
        assertEquals("我的调音", back.get(0).name);
        for (int i = 0; i < GraphicEQ.BANDS; i++) {
            assertEquals(bands(0f)[i], back.get(0).bands[i], 0.001f);
        }
    }

    @Test
    public void roundTrip_many() {
        // 用户要求数量不设小上限：测 1000 组往返
        List<EqPresets.Preset> list = new ArrayList<>();
        for (int k = 0; k < 1000; k++) {
            list.add(new EqPresets.Preset("P" + k, bands(k % 10)));
        }
        String raw = EqPresets.serialize(list);
        List<EqPresets.Preset> back = EqPresets.parse(raw);
        assertEquals(1000, back.size());
        assertEquals("P999", back.get(999).name);
    }

    @Test
    public void sanitizesDelimiters() {
        List<EqPresets.Preset> list = new ArrayList<>();
        list.add(new EqPresets.Preset("a|b;c", bands(0f)));
        List<EqPresets.Preset> back = EqPresets.parse(EqPresets.serialize(list));
        assertEquals(1, back.size());
        assertEquals("a_b_c", back.get(0).name);
    }

    @Test
    public void malformedInput_isTolerated() {
        assertTrue(EqPresets.parse(null).isEmpty());
        assertTrue(EqPresets.parse("").isEmpty());
        assertTrue(EqPresets.parse("garbage").isEmpty());
        assertTrue(EqPresets.parse("name|1,2,3").isEmpty()); // 段数不足
        assertEquals(1, EqPresets.parse("ok|" + join(bands(0f)) + ";bad|1").size());
    }

    private static String join(float[] b) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(b[i]);
        }
        return sb.toString();
    }
}
