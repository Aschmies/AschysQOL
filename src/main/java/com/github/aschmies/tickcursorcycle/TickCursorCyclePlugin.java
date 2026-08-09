/*
 * Copyright (c) 2026, Aschy
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.github.aschmies.tickcursorcycle;

import com.google.inject.Provides;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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
	tags = {"cursor", "tick", "colour", "mouse", "highlight"},
	enabledByDefault = false
)
public class TickCursorCyclePlugin extends Plugin
{
	// Polygon fallback dimensions (used only if the system cursor file can't be loaded)
	private static final int CURSOR_SIZE = 32;
	private static final int[] ARROW_X = { 1,  1,  5,  7, 10,  7, 13};
	private static final int[] ARROW_Y = { 1, 22, 16, 22, 19, 13, 13};

	@Inject
	private ClientUI clientUI;

	@Inject
	private TickCursorCycleConfig config;

	private int tickIndex;
	/** Unmodified system cursor image used as the tint base; null = use polygon fallback. */
	private BufferedImage baseCursor;

	@Provides
	TickCursorCycleConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TickCursorCycleConfig.class);
	}

	@Override
	protected void startUp()
	{
		baseCursor = loadSystemCursor();
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

	/**
	 * Reads the Windows default arrow cursor from the system .cur file (DIB or PNG format).
	 * Prefers the 32×32 entry; falls back to the smallest available size.
	 */
	private static BufferedImage loadSystemCursor()
	{
		String sysRoot = System.getenv("SystemRoot");
		if (sysRoot == null)
		{
			return null;
		}
		String[] candidates = {"aero_arrow.cur", "arrow_r.cur"};
		for (String name : candidates)
		{
			try
			{
				byte[] data = Files.readAllBytes(Paths.get(sysRoot, "Cursors", name));
				int count = (data[4] & 0xFF) | ((data[5] & 0xFF) << 8);

				// Prefer 32×32; otherwise take the smallest available entry
				int chosen = -1;
				int chosenW = Integer.MAX_VALUE;
				for (int i = 0; i < count; i++)
				{
					int base = 6 + i * 16;
					if (base + 16 > data.length) break;
					int w = data[base] & 0xFF;
					if (w == 0) w = 256;
					int sz  = readInt32(data, base + 8);
					int off = readInt32(data, base + 12);
					if (off < 0 || sz <= 0 || off + sz > data.length) continue;
					if (w == 32) { chosen = i; break; }          // exact match wins immediately
					if (w < chosenW) { chosenW = w; chosen = i; } // otherwise keep smallest
				}
				if (chosen < 0) continue;

				int base   = 6 + chosen * 16;
				int imgSz  = readInt32(data, base + 8);
				int imgOff = readInt32(data, base + 12);

				// Detect PNG (magic 0x89 0x50) vs. DIB (BITMAPINFOHEADER starts with 0x28 0x00 0x00 0x00)
				if (data[imgOff] == (byte) 0x89 && data[imgOff + 1] == 0x50)
				{
					BufferedImage img = ImageIO.read(new ByteArrayInputStream(data, imgOff, imgSz));
					if (img != null) return img;
				}
				else
				{
					// DIB: BITMAPINFOHEADER + AND mask + XOR (BGRA) pixel data
					BufferedImage img = parseDibCursor(data, imgOff);
					if (img != null) return img;
				}
			}
			catch (IOException e)
			{
				log.debug("Could not read cursor file {}", name, e);
			}
		}
		log.debug("System cursor unavailable — using polygon fallback");
		return null;
	}

	/**
	 * Parses a 32-bit DIB cursor image from a raw CUR/ICO image payload.
	 * Layout: 40-byte BITMAPINFOHEADER, AND mask, then bottom-up BGRA XOR rows.
	 */
	private static BufferedImage parseDibCursor(byte[] data, int offset)
	{
		if (offset + 40 > data.length) return null;
		int biWidth    = readInt32(data, offset + 4);
		int biHeight   = readInt32(data, offset + 8); // = 2 × actual height in CUR files
		int biBitCount = (data[offset + 14] & 0xFF) | ((data[offset + 15] & 0xFF) << 8);
		if (biBitCount != 32 || biWidth <= 0 || biHeight <= 0) return null;

		int h = biHeight / 2;
		int w = biWidth;
		// In ICO/CUR DIBs: XOR (color) data comes first, AND mask comes after
		int xorStart = offset + 40;
		if (xorStart + w * h * 4 > data.length) return null;

		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		for (int row = 0; row < h; row++)
		{
			// DIB rows are bottom-up: row 0 in file = bottom row of image
			int imgRow = h - 1 - row;
			int rowBase = xorStart + row * w * 4;
			for (int col = 0; col < w; col++)
			{
				int p = rowBase + col * 4;
				int b = data[p]     & 0xFF;
				int g = data[p + 1] & 0xFF;
				int r = data[p + 2] & 0xFF;
				int a = data[p + 3] & 0xFF;
				img.setRGB(col, imgRow, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
		return img;
	}

	private static int readInt32(byte[] data, int off)
	{
		return (data[off] & 0xFF)
			| ((data[off + 1] & 0xFF) << 8)
			| ((data[off + 2] & 0xFF) << 16)
			| ((data[off + 3] & 0xFF) << 24);
	}

	/**
	 * Multiply-blends the user's colour over the grayscale cursor image.
	 * Black pixels (outline) stay black; white pixels become the chosen colour.
	 */
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

	/** Polygon fallback used when the system cursor file cannot be loaded. */
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
