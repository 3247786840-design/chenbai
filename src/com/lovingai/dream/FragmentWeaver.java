package com.lovingai.dream;

import com.lovingai.core.Circle;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 从「训练完成」的圆中随机抽取知识/记忆/活动碎片，织成梦与爱的原料；
 * 若尚无成熟圆，则退化为成熟度较低的圆以保证可观测性。
 */
public final class FragmentWeaver {

    private FragmentWeaver() {}

    public static String weaveFromTrainedCircles(List<Circle> galaxy, ThreadLocalRandom rng) {
        if (galaxy == null || galaxy.isEmpty()) {
            return "";
        }
        List<Circle> trained = new ArrayList<>();
        for (Circle c : galaxy) {
            if (c.isTrainingComplete()) {
                trained.add(c);
            }
        }
        if (trained.isEmpty()) {
            for (Circle c : galaxy) {
                if (!c.isBootstrapRing() && c.maturity >= 12.0) {
                    trained.add(c);
                }
            }
        }
        if (trained.isEmpty()) {
            return "";
        }

        int pieces = 2 + rng.nextInt(3);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < pieces; i++) {
            Circle c = trained.get(rng.nextInt(trained.size()));
            String f = c.sampleFragment(rng);
            if (f == null || f.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append('「').append(c.name).append("」").append(f);
        }
        return sb.toString();
    }
}
