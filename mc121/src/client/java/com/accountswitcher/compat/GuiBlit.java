package com.accountswitcher.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Cross-version textured blit for GuiGraphics / DrawContext.
 * 1.21.6+ requires a {@code RenderPipeline} (prefer {@code GUI_TEXTURED}).
 */
public final class GuiBlit {
	private static final Method BLIT = findBlit();
	private static final Object GUI_PIPELINE = findGuiPipeline();
	private static boolean loggedFailure;

	private GuiBlit() {
	}

	private static Method findBlit() {
		Method nine = null;
		Method ten = null;
		Method eleven = null;
		for (Method method : GuiGraphics.class.getMethods()) {
			if (Modifier.isStatic(method.getModifiers())) {
				continue;
			}
			Class<?>[] p = method.getParameterTypes();
			if (p.length == 9
					&& isResourceId(p[0])
					&& p[1] == int.class && p[2] == int.class
					&& (p[3] == float.class || p[3] == int.class)
					&& (p[4] == float.class || p[4] == int.class)
					&& p[5] == int.class && p[6] == int.class
					&& p[7] == int.class && p[8] == int.class) {
				nine = method;
			} else if (p.length == 10
					&& isResourceId(p[1])
					&& p[2] == int.class && p[3] == int.class
					&& p[4] == float.class && p[5] == float.class
					&& p[6] == int.class && p[7] == int.class
					&& p[8] == int.class && p[9] == int.class) {
				ten = method;
			} else if (p.length == 11
					&& isResourceId(p[1])
					&& p[2] == int.class && p[3] == int.class
					&& p[4] == float.class && p[5] == float.class
					&& p[6] == int.class && p[7] == int.class
					&& p[8] == int.class && p[9] == int.class
					&& p[10] == int.class) {
				eleven = method;
			}
		}
		Method chosen = nine != null ? nine : (ten != null ? ten : eleven);
		if (chosen != null) {
			chosen.setAccessible(true);
		}
		return chosen;
	}

	private static boolean isResourceId(Class<?> type) {
		return ResourceLocation.class.isAssignableFrom(type)
				|| "ResourceLocation".equals(type.getSimpleName())
				|| "Identifier".equals(type.getSimpleName());
	}

	private static Object findGuiPipeline() {
		if (BLIT == null || BLIT.getParameterCount() < 10) {
			return null;
		}
		Class<?> pipelineType = BLIT.getParameterTypes()[0];
		String[] holders = {
				"net.minecraft.client.gl.RenderPipelines",
				"net.minecraft.client.renderer.RenderPipelines",
				"net.minecraft.client.gui.RenderPipelines",
				"com.mojang.blaze3d.pipeline.RenderPipelines"
		};
		ClassLoader cl = GuiGraphics.class.getClassLoader();
		for (String holderName : holders) {
			try {
				Class<?> holder = Class.forName(holderName, true, cl);
				Object value = findPipelineField(holder, pipelineType);
				if (value != null) {
					return value;
				}
			} catch (ClassNotFoundException ignored) {
			}
		}
		return findPipelineField(pipelineType, pipelineType);
	}

	private static Object findPipelineField(Class<?> holder, Class<?> pipelineType) {
		Object guiTextured = null;
		Object gui = null;
		Object fallback = null;
		for (Field field : holder.getFields()) {
			if (!Modifier.isStatic(field.getModifiers()) || !pipelineType.isAssignableFrom(field.getType())) {
				continue;
			}
			try {
				Object value = field.get(null);
				if (value == null) {
					continue;
				}
				String name = field.getName().toUpperCase();
				if (name.equals("GUI_TEXTURED") || (name.contains("GUI") && name.contains("TEXTURED") && !name.contains("PREMULTIPLIED"))) {
					guiTextured = value;
				} else if (name.equals("GUI")) {
					gui = value;
				} else if (fallback == null) {
					fallback = value;
				}
			} catch (IllegalAccessException ignored) {
			}
		}
		if (guiTextured != null) {
			return guiTextured;
		}
		if (gui != null) {
			return gui;
		}
		return fallback;
	}

	public static boolean blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
		if (texture == null || BLIT == null) {
			if (!loggedFailure) {
				loggedFailure = true;
				com.accountswitcher.AccountSwitcherClient.LOGGER.warn(
						"GuiBlit unavailable (blit={}, pipeline={})",
						BLIT != null,
						GUI_PIPELINE != null
				);
			}
			return false;
		}
		try {
			Class<?>[] p = BLIT.getParameterTypes();
			if (p.length == 9) {
				Object u = p[3] == float.class ? Float.valueOf(0f) : Integer.valueOf(0);
				Object v = p[4] == float.class ? Float.valueOf(0f) : Integer.valueOf(0);
				BLIT.invoke(graphics, texture, x, y, u, v, width, height, width, height);
				return true;
			}
			if (p.length >= 10 && GUI_PIPELINE != null) {
				if (p.length == 10) {
					BLIT.invoke(graphics, GUI_PIPELINE, texture, x, y, 0f, 0f, width, height, width, height);
				} else {
					BLIT.invoke(graphics, GUI_PIPELINE, texture, x, y, 0f, 0f, width, height, width, height, 0xFFFFFFFF);
				}
				return true;
			}
			if (!loggedFailure) {
				loggedFailure = true;
				com.accountswitcher.AccountSwitcherClient.LOGGER.warn(
						"GuiBlit found method but no GUI pipeline (params={})",
						p.length
				);
			}
			return false;
		} catch (Throwable t) {
			if (!loggedFailure) {
				loggedFailure = true;
				com.accountswitcher.AccountSwitcherClient.LOGGER.warn("GuiBlit invoke failed", t);
			}
			return false;
		}
	}
}
