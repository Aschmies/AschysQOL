package com.github.aschmies.tickcursorcycle;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("tickcursorcycle")
public interface TickCursorCycleConfig extends Config
{
	@Range(min = 1, max = 6)
	@ConfigItem(
		position = 0,
		keyName = "tickCount",
		name = "Tick count",
		description = "How many ticks to cycle through (1â€“6). Colour slots beyond this number are ignored."
	)
	default int tickCount()
	{
		return 2;
	}

	@Alpha
	@ConfigItem(
		position = 1,
		keyName = "color1",
		name = "Tick 1 colour",
		description = "Cursor colour on tick 1"
	)
	default Color color1()
	{
		return new Color(255, 255, 255, 220);
	}

	@Alpha
	@ConfigItem(
		position = 2,
		keyName = "color2",
		name = "Tick 2 colour",
		description = "Cursor colour on tick 2"
	)
	default Color color2()
	{
		return new Color(30, 144, 255, 230);
	}

	@Alpha
	@ConfigItem(
		position = 3,
		keyName = "color3",
		name = "Tick 3 colour",
		description = "Cursor colour on tick 3 (unused when tick count < 3)"
	)
	default Color color3()
	{
		return new Color(105, 105, 105, 230);
	}

	@Alpha
	@ConfigItem(
		position = 4,
		keyName = "color4",
		name = "Tick 4 colour",
		description = "Cursor colour on tick 4 (unused when tick count < 4)"
	)
	default Color color4()
	{
		return new Color(147, 112, 219, 230);
	}

	@Alpha
	@ConfigItem(
		position = 5,
		keyName = "color5",
		name = "Tick 5 colour",
		description = "Cursor colour on tick 5 (unused when tick count < 5)"
	)
	default Color color5()
	{
		return new Color(60, 179, 113, 230);
	}

	@Alpha
	@ConfigItem(
		position = 6,
		keyName = "color6",
		name = "Tick 6 colour",
		description = "Cursor colour on tick 6 (unused when tick count < 6)"
	)
	default Color color6()
	{
		return new Color(218, 165, 32, 230);
	}
}
