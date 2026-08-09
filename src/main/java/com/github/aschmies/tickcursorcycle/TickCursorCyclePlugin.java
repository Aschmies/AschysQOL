package com.github.aschmies.tickcursorcycle;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientUI;

@Slf4j
@PluginDescriptor(
name = "Tick Cursor",
description = "Cycles the mouse cursor colour on each game tick",
tags = {"cursor", "tick", "colour", "mouse", "highlight"}
)
public class TickCursorCyclePlugin extends Plugin
{
// Polygon fallback dimensions (used only if the bundled cursor resource can't be loaded)
private static final int CURSOR_SIZE = 32;
private static final int[] ARROW_X = { 1,  1,  5,  7, 10,  7, 13};
private static final int[] ARROW_Y = { 1, 22, 16, 22, 19, 13, 13};

@Inject
private ClientUI clientUI;

@Inject
private TickCursorCycleConfig config;

private int tickIndex;
/** Unmodified cursor image used as the tint base; null = use polygon fallback. */
private BufferedImage baseCursor;

@Provides
TickCursorCycleConfig provideConfig(ConfigManager configManager)
{
return configManager.getConfig(TickCursorCycleConfig.class);
}

@Override
protected void startUp()
{
baseCursor = loadCursor();
tickIndex = 0;
updateCursor();
}

@Override
protected void shutDown()
{
baseCursor = null;
clientUI.resetCursor();
}

@Subscribe
public void onGameTick(GameTick event)
{
tickIndex = (tickIndex + 1) % config.tickCount();
updateCursor();
}

@Subscribe
public void onConfigChanged(ConfigChanged event)
{
if (!event.getGroup().equals("tickcursorcycle"))
{
return;
}
if ("tickCount".equals(event.getKey()))
{
tickIndex = 0;
}
updateCursor();
}

private void updateCursor()
{
Color colour = colourForTick(tickIndex);
BufferedImage image = baseCursor != null ? tintCursor(baseCursor, colour) : buildPolygonCursor(colour);
clientUI.setCursor(image, "tick-cursor");
}

private Color colourForTick(int index)
{
switch (index)
{
case 1: return config.color2();
case 2: return config.color3();
case 3: return config.color4();
case 4: return config.color5();
case 5: return config.color6();
default: return config.color1();
}
}

// ---- cursor image helpers ----

private BufferedImage loadCursor()
{
try (InputStream is = getClass().getResourceAsStream("cursor.png"))
{
if (is == null) return null;
return ImageIO.read(is);
}
catch (IOException e)
{
log.debug("Could not load bundled cursor", e);
return null;
}
}

/** Multiply-blends the user's colour over the grayscale cursor image. */
private static BufferedImage tintCursor(BufferedImage source, Color colour)
{
int w = source.getWidth();
int h = source.getHeight();
BufferedImage result = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
float cr = colour.getRed()   / 255f;
float cg = colour.getGreen() / 255f;
float cb = colour.getBlue()  / 255f;
float ca = colour.getAlpha() / 255f;

for (int y = 0; y < h; y++)
{
for (int x = 0; x < w; x++)
{
int argb = source.getRGB(x, y);
int sa = (argb >>> 24) & 0xFF;
if (sa == 0)
{
continue;
}
int sr = (argb >>> 16) & 0xFF;
int sg = (argb >>> 8)  & 0xFF;
int sb =  argb         & 0xFF;
float lum = (sr + sg + sb) / (3f * 255f);

int nr = Math.min(255, Math.round(cr * lum * 255f));
int ng = Math.min(255, Math.round(cg * lum * 255f));
int nb = Math.min(255, Math.round(cb * lum * 255f));
int na = Math.min(255, Math.round(sa * ca));

result.setRGB(x, y, (na << 24) | (nr << 16) | (ng << 8) | nb);
}
}
return result;
}

/** Polygon fallback used when the bundled cursor resource cannot be loaded. */
private static BufferedImage buildPolygonCursor(Color color)
{
BufferedImage image = new BufferedImage(CURSOR_SIZE, CURSOR_SIZE, BufferedImage.TYPE_INT_ARGB);
Graphics2D g = image.createGraphics();
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

Polygon arrow = new Polygon(ARROW_X, ARROW_Y, ARROW_X.length);

// draw black border first; fill on top leaves only the outer edge of stroke visible
g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
g.setColor(new Color(0, 0, 0, color.getAlpha()));
g.draw(arrow);

g.setColor(color);
g.fill(arrow);

g.dispose();
return image;
}
}