package com.breakerch.yogiri.compat;

import net.minecraft.world.entity.Entity;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Optional Pehkui bridge. Reflection keeps Pehkui optional and avoids a hard dependency.
 * If Pehkui is absent or its API differs, the entity still works at its vanilla size.
 */
public final class PehkuiCompat {
    private static boolean checked = false;
    private static boolean available = false;
    private static Method getScaleData;
    private static Method setScale;

    private PehkuiCompat() {}

    public static void applyScale(Entity entity, float scale) {
        try {
            if (!checked) discover();
            if (!available) return;
            Object baseType = Class.forName("virtuoel.pehkui.api.ScaleTypes")
                    .getField("BASE").get(null);
            Object data = getScaleData.invoke(baseType, entity);
            setScale.invoke(data, scale);
        } catch (Throwable ignored) {
            // Compatibility must never crash a world if Pehkui is missing/incompatible.
        }
    }

    private static void discover() {
        checked = true;
        try {
            Class<?> scaleTypeClass = Class.forName("virtuoel.pehkui.api.ScaleType");
            Class<?> scaleDataClass = Class.forName("virtuoel.pehkui.api.ScaleData");
            getScaleData = scaleTypeClass.getMethod("getScaleData", Entity.class);
            setScale = scaleDataClass.getMethod("setScale", float.class);
            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }
}
