package com.lootchance;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import java.awt.image.BufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import javax.inject.Inject;

@Slf4j
@PluginDescriptor(
		name = "LootChance",
		description = "Track cumulative drop chances based on table chance and kill/loot count using OSRS Wiki data.",
		tags = {"loot", "drop", "chance", "probability"}
)
public class LootChancePlugin extends Plugin
{

	@SuppressWarnings("unused")
	@Inject
	private ClientToolbar clientToolbar;

	LootChancePanel panel;
	private NavigationButton navButton;

	private static final BufferedImage ICON = ImageUtil.loadImageResource(LootChancePlugin.class, "/util/panel_icon.png");

	@Override
	protected void startUp()
	{
		panel = new LootChancePanel();

		navButton = NavigationButton.builder()
				.tooltip("LootChance")
				.icon(ICON)
				.panel(panel)
				.priority(5)
				.build();

		clientToolbar.addNavigation(navButton);

		log.info("LootChance started!");
	}

	@Override
	protected void shutDown()
	{
		clientToolbar.removeNavigation(navButton);
		log.info("LootChance stopped.");
	}
}
