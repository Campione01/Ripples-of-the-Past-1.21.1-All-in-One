package rotp.core.powersystem.standpower.client_screens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import rotp.core.client.standskin.StandSkin;
import rotp.core.client.standskin.StandSkinsLoader;
import rotp.core.client.standskin.sprites.AbilityIconSprites;
import rotp.core.client.textsymbols.IconSymbols;
import rotp.core.client.ui.screen_jojomenu.IJojoMenuScreen;
import rotp.core.client.ui.screen_jojomenu.JojoMenuTabs;
import rotp.core.client.ui.screen_jojomenu.PaperButton;
import rotp.core.client.ui.screen_jojomenu.Tab;
import rotp.core.client.ui.screen_jojomenu.TabCategory;
import rotp.core.client.ui.screen_widgets.ImageButton2;
import rotp.core.client.ui.text.StandSkillText;
import rotp.core.client.ui.utils.BlitFloat;
import rotp.core.client.ui.utils.GuiIcon;
import rotp.core.client.ui.utils.Scrolling;
import rotp.core.client.ui.utils.ScrollingText;
import rotp.core.client.ui.utils.SkillListLayout;
import rotp.core.client.ui.utils.TextBoxFit;
import rotp.core.client.ui.utils.tooltip.MutableTooltipWrapper;
import rotp.core.client.ui.utils.tooltip.TooltipParams;
import rotp.core.core.JojoMod;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.TrainableAbility;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.powersystem.standpower.type.StandTypePersistentData.StandExpSummary;
import rotp.core.powersystem.unlockableskill.ClLearnSkillPacket;
import rotp.core.powersystem.unlockableskill.UnlockableSkill;
import rotp.core.powersystem.unlockableskill.UnlockableSkill.DevStatus;
import com.google.common.collect.Iterables;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.network.PacketDistributor;

public class StandSkillsScreen extends Screen implements IJojoMenuScreen {
	public static final ResourceLocation WINDOW = JojoMod.resLoc("textures/gui/paper_style/stand_skills.png");
	public static final GuiIcon SCROLL_BAR = new GuiIcon(WINDOW, 243, 58, 5, 162, 256, 256);
	public static final GuiIcon CROSS = new GuiIcon(JojoMod.resLoc("textures/gui/sprites/widget/cross8.png"), 8, 8);
	public static final GuiIcon CROSS_HIGHLIGHTED = new GuiIcon(JojoMod.resLoc("textures/gui/sprites/widget/cross8_highlighted.png"), 8, 8);
	
	protected TabCategory category;
	protected Tab tab;

	protected StandPower standPower;
	protected StandTypePersistentData levelingData;
	protected StandSkin standSkin;
	protected Iterable<StandUnlockableSkill> skills;
	@Nullable protected StandUnlockableSkill selectedSkill;
	protected List<Ability> passives = List.of();
	@Nullable protected Ability selectedPassive;
	@Nullable private String passiveStateShown;
	
	protected Scrolling skillListScrolling;
	protected ScrollingText skillDescription;
	protected ScrollingText skillControls;
	
	protected Button learnSkillButton;
	protected Button trainSkillButton;
	protected Button resetSkillsButton;
	protected Button learnAllSkillsButton;
	protected MutableTooltipWrapper learnSkillTooltip;
	protected Map<String, ConditionCheck> unlockSkillChecks = new HashMap<>();
	
	protected Button deselectSkillButton;

	public StandSkillsScreen(Component title, TabCategory category, Tab tab) {
		this(title, category, tab,
				JojoMenuTabs.getPowerForMenu(PowerClass.STAND));
	}

	public StandSkillsScreen(
			Component title,
			TabCategory category,
			Tab tab,
			StandPower standPower) {
		super(title);
		this.category = category;
		this.tab = tab;
		this.standPower = standPower;
	}

	@Override
	public TabCategory getTabCategory() {
		return category;
	}

	@Override
	public Tab getTab() {
		return tab;
	}

	@Override
    protected void init() {
		int x = getWindowX(this);
		int y = getWindowY(this);
		
		levelingData = standPower.getCurTypeData();
		skills = standPower.getPowerType().getUnlockableSkills().values().stream()
				.filter(skill -> !skill.hidden)
				.toList();
		passives = passivesOf(standPower);
		skillListScrolling = new Scrolling(162, SkillListLayout.height(Iterables.size(skills), passives.size()));
		standSkin = StandSkinsLoader.getInstance().getSkin(standPower);

		learnSkillButton = this.addRenderableWidget(new PaperButton(x + 144, y + 201, 80, 20, 
				Component.translatable("jojo_ripples.stand_skills.learn"), 
				button -> {
					if (standPower != null && standPower.hasPower() && selectedSkill != null) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnSkill(
								PowerClass.STAND, standPower.getPowerType().getId(), selectedSkill.skillName));
					}
				}));
		learnSkillButton.setTooltip(learnSkillTooltip = new MutableTooltipWrapper() {
			@Override
			public Tooltip updateToolip() {
				if (selectedSkill != null) {
					ConditionCheck canLearn = unlockSkillChecks.get(selectedSkill.skillName);
					if (!canLearn.isPositive()) {
						Component message = canLearn.getWarning();
						if (message != null) {
							return Tooltip.create(message.plainCopy().withStyle(ChatFormatting.RED));
						}
					}
				}
				return null;
			}
			
		});
		
		trainSkillButton = this.addRenderableWidget(new PaperButton(x + 144, y + 201, 80, 20,
				Component.translatable("jojo_ripples.stand_skills.train"),
				button -> {
					if (standPower != null && standPower.hasPower() && selectedSkill != null && minecraft.player != null && minecraft.player.isCreative()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.trainSkill(
								PowerClass.STAND, standPower.getPowerType().getId(), selectedSkill.skillName));
					}
				}));
		trainSkillButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.stand_skills.train.creative_only")));
		
		resetSkillsButton = this.addRenderableWidget(new PaperButton(x + 154, y + 201, 70, 20, 
				Component.translatable("jojo_ripples.stand_skills.reset"), 
				button -> {
					if (standPower != null && standPower.hasPower()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.resetAll(
								PowerClass.STAND, standPower.getPowerType().getId()));
					}
				}));
		resetSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		
		learnAllSkillsButton = this.addRenderableWidget(new PaperButton(x + 80, y + 201, 70, 20, 
				Component.translatable("jojo_ripples.stand_skills.learn_all"), 
				button -> {
					if (standPower != null && standPower.hasPower()) {
						PacketDistributor.sendToServer(ClLearnSkillPacket.learnAll(
								PowerClass.STAND, standPower.getPowerType().getId()));
					}
				}));
		learnAllSkillsButton.setTooltip(Tooltip.create(Component.translatable("jojo_ripples.note.creative_only")));
		
		skillDescription = new ScrollingText(x + 86, y + 87, 124, 105);
		skillControls = new ScrollingText(x + 100, y + 49, 117, 31);
		Ability keptPassive = selectedPassive;
		setSelectedSkill(this.selectedSkill);
		if (keptPassive != null) {
			String name = keptPassive.name();
			setSelectedPassive(passives.stream().filter(passive -> passive.name().equals(name)).findFirst().orElse(null));
		}
		
		deselectSkillButton = addRenderableWidget(new ImageButton2(x + 68, y + 23, 8, 8, 
				CROSS, CROSS, CROSS_HIGHLIGHTED, CROSS_HIGHLIGHTED, 
				button -> setSelectedSkill(null)) {
			@Override public void playDownSound(SoundManager handler) {}
		});
	}

	protected static final int SKILL_LIST_X = 22;
	protected static final int SKILL_LIST_Y = 57;
	// the header's title room: right of the deselect cross, left of the window's right margin
	private static final int TITLE_LEFT = 79;
	private static final int TITLE_RIGHT_MARGIN = 10;
	private static final int DESC_TEXT_WIDTH = 111;
	private static final int CONTROLS_TEXT_WIDTH = 101;
	@Nullable private Component cutTitle;
	@Override
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float p_283123_) {
		deselectSkillButton.visible = selectedSkill != null || selectedPassive != null;
		StandExpSummary expSummary = levelingData.expSummary(standPower);
		
		this.renderBackground(guiGraphics, mouseX, mouseY, p_283123_);
		
		for (StandUnlockableSkill skill : skills) {
			unlockSkillChecks.put(skill.skillName, skill.canUnlockFromMenu(standPower, levelingData));
		}
		
		learnSkillButton.visible = selectedSkill != null && !levelingData.isSkillUnlocked(selectedSkill.skillName);
		learnSkillButton.active = selectedSkill != null && unlockSkillChecks.get(selectedSkill.skillName).isPositive();
		TrainableAbility selectedTrainable = getSelectedTrainableAbility();
		boolean selectedSkillUnlocked = selectedSkill != null && levelingData.isSkillUnlocked(selectedSkill.skillName);
		boolean canDirectTrain = minecraft.player != null && minecraft.player.isCreative();
		trainSkillButton.visible = selectedSkillUnlocked && selectedTrainable != null && canDirectTrain;
		trainSkillButton.active = trainSkillButton.visible && !isTrainingComplete(selectedTrainable);
		
		resetSkillsButton.visible = selectedSkill == null && selectedPassive == null && minecraft.player.isCreative();
		learnAllSkillsButton.visible = selectedSkill == null && selectedPassive == null && minecraft.player.isCreative();
		
		int x = getWindowX(this);
		int y = getWindowY(this);
		int width = getWindowWidth();
		int height = getWindowHeight();
		BlitFloat.blit(guiGraphics.pose(), Minecraft.getInstance(), WINDOW, 
				x, y, width, height, 0, 
				0, 0, width, height, 256, 256, 
				BlitFloat.NO_TINT);
//		int textColor = standSkin.getColor();
		int textColor = 0xFF000000;

		int skillListX = x + SKILL_LIST_X;
		int skillListY = y + SKILL_LIST_Y;
		skillListScrolling.pushOffsetScissor(guiGraphics, skillListY + 1, skillListX + 1, skillListX + 60);
		
		int spriteX = skillListX + 2;
		int spriteY = skillListY + 4;
		AbilityIconSprites abilityIconSprites = StandSkinsLoader.getInstance().abilityIcons;
		UnlockableSkill hovered = getHoveredSkill(mouseX, mouseY);
		
		for (StandUnlockableSkill skill : skills) {
			boolean isUnlocked = levelingData.isSkillUnlocked(skill.skillName);
			int expCostColor = !isUnlocked ? getExpCostColor(skill) : STAND_EXP_NUMBER_COLOR;
			boolean unlockedOrOnlyMissingStandExp = expCostColor == STAND_EXP_NUMBER_COLOR;
			int skillSpriteColor = unlockedOrOnlyMissingStandExp ? BlitFloat.NO_TINT : 0x40404040;
			
			TextureAtlasSprite icon = abilityIconSprites.getSkillIcon(skill, standPower, standSkin);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			BlitFloat.blit(guiGraphics.pose(), minecraft, icon, 
					spriteX, spriteY, 16, 16, 0, skillSpriteColor);
			
			if (isUnlocked) {
				guiGraphics.drawString(font, String.valueOf(IconSymbols.CHECKMARK), spriteX + 18, spriteY + 4, 0xFFFFFFFF);
			}
			else {
				int expToUnlock = skill.expToUnlock;
				String expCostLine = expToUnlock > 0 ? String.valueOf(expToUnlock) : "-";
				guiGraphics.drawString(font, expCostLine, spriteX + 18, spriteY + 4, expCostColor, false);
			}
			if (skill.implemented.rowTint != 0) {
				guiGraphics.fill(spriteX - 1, spriteY - 1, spriteX + 37, spriteY + 17, skill.implemented.rowTint);
			}
			spriteY += 20;
		}
		Ability hoveredPassive = getHoveredPassive(mouseX, mouseY);
		float passivesLabelScale = renderPassives(guiGraphics, skillListX, skillListY, abilityIconSprites, p_283123_);

		skillListScrolling.pop(guiGraphics);
		skillListScrolling.renderScrollBar(skillListX - 8, skillListY + 1, 0, 4, guiGraphics, SCROLL_BAR, 1);

		if (selectedSkill != null) {
			updateSelectedSkillText(selectedTrainable);
			Component name = skinSkillName(selectedSkill);
			int titleRight = x + getWindowWidth() - TITLE_RIGHT_MARGIN;
			FormattedCharSequence title = fitTitle(name, titleRight - (x + TITLE_LEFT));
			guiGraphics.drawString(font, title, titleRight - font.width(title), y + 24, textColor, false);
			cutTitle = font.width(name) > titleRight - (x + TITLE_LEFT) ? name : null;
			
			skillDescription.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillDescription.drawSmallScrollBar(guiGraphics);
			skillControls.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillControls.drawSmallScrollBar(guiGraphics);
		}
		else if (selectedPassive != null) {
			updatePassiveState(p_283123_);
			Component name = selectedPassive.getName(standPower);
			int titleRight = x + getWindowWidth() - TITLE_RIGHT_MARGIN;
			FormattedCharSequence title = fitTitle(name, titleRight - (x + TITLE_LEFT));
			guiGraphics.drawString(font, title, titleRight - font.width(title), y + 24, textColor, false);
			cutTitle = font.width(name) > titleRight - (x + TITLE_LEFT) ? name : null;

			skillDescription.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillDescription.drawSmallScrollBar(guiGraphics);
			skillControls.draw(4, 3, guiGraphics, this.minecraft.font, textColor, false);
			skillControls.drawSmallScrollBar(guiGraphics);
		}
		
		int maxExp = expSummary.total - expSummary.spent;
		int exp = Math.min(levelingData.getExp(), maxExp);
		Component expLine = exp < maxExp ? Component.literal(String.valueOf(exp)) : Component.translatable("jojo_ripples.stand_exp.max");
		expLine = Component.literal(String.valueOf(IconSymbols.STAND_EXP)).append(expLine);
		guiGraphics.drawString(font, expLine, x + 41 - font.width(expLine) / 2, y + 33, STAND_EXP_NUMBER_COLOR, false);
		
		if (learnSkillButton.visible && selectedSkill != null) {
			int expCostColor = getExpCostColor(selectedSkill);
			if (expCostColor == STAND_EXP_NUMBER_COLOR) {
				Component expCost = Component.literal(IconSymbols.STAND_EXP + " " + String.valueOf(selectedSkill.expToUnlock));
				guiGraphics.drawString(font, expCost, 
						learnSkillButton.getX() - 4 - font.width(expCost), learnSkillButton.getY() + 6, 
						expCostColor, false);
			}
		}
		
		renderTabs(guiGraphics, this);
		
		// the selected row's name is in the header: its tooltip would only cover the controls box under the mouse that just clicked it
		if (hovered != null && (hovered != selectedSkill || hovered.implemented != DevStatus.IMPLEMENTED)) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			List<FormattedCharSequence> skillNameTooltip = new ArrayList<>();
			skillNameTooltip.add(skinSkillName(hovered).copy().withStyle(ChatFormatting.BLACK).getVisualOrderText());
			switch (hovered.implemented) {
				case WIP -> skillNameTooltip.add(Component.translatable("rotp_tag_wip")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC).getVisualOrderText());
				case NYI -> skillNameTooltip.add(Component.translatable("rotp_tag_nyi")
						.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC).getVisualOrderText());
				default -> {}
			}
			setTooltipForNextRenderPass(skillNameTooltip);
		}
		else if (hoveredPassive != null && hoveredPassive != selectedPassive) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			setTooltipForNextRenderPass(List.of(hoveredPassive.getName(standPower).copy().withStyle(ChatFormatting.BLACK)
					.getVisualOrderText()));
		}
		else if (passivesLabelScale > 0 && isOverPassivesLabel(mouseX, mouseY)) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			setTooltipForNextRenderPass(List.of(Component.translatable(PASSIVES_KEY).withStyle(ChatFormatting.BLACK)
					.getVisualOrderText()));
		}
		else if ((selectedSkill != null || selectedPassive != null) && cutTitle != null && mouseX - x >= TITLE_LEFT
				&& mouseX - x <= getWindowWidth() - TITLE_RIGHT_MARGIN && mouseY - y >= 20 && mouseY - y <= 34) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			setTooltipForNextRenderPass(List.of(cutTitle.copy().withStyle(ChatFormatting.BLACK).getVisualOrderText()));
		}
		else if (mouseX - x >= 22 && mouseX - x <= 59 && mouseY - y >= 30 && mouseY - y <= 43) {
			TooltipParams.set(TooltipParams.paperStyle(1));
			List<FormattedCharSequence> standExpTooltip = new ArrayList<>();
			standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp")
					.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.total", expSummary.spent + exp, expSummary.total)
					.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			if (expSummary.remainingHiddenSkills > 0) {
				standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.skills_left.hidden", expSummary.remainingSkills, expSummary.remainingHiddenSkills)
						.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			}
			else {
				standExpTooltip.add(Component.translatable("jojo_ripples.stand_exp.skills_left", expSummary.remainingSkills)
						.withStyle(ChatFormatting.BLACK).getVisualOrderText());
			}
			standExpTooltip.addAll(Tooltip.splitTooltip(minecraft, Component.translatable("jojo_ripples.stand_exp.desc")
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
			setTooltipForNextRenderPass(standExpTooltip);
		}
		else {
			renderTabTooltip(guiGraphics, this, mouseX, mouseY);
		}

		for (Renderable renderable : this.renderables) {
			renderable.render(guiGraphics, mouseX, mouseY, p_283123_);
		}
	}
	
	public static final int STAND_EXP_NUMBER_COLOR = 0x00A000;
	public static final String PASSIVES_KEY = "jojo_ripples.stand_skills.passives";
	private static final int PASSIVE_LINE_COLOR = 0x60000000;
	private static final int PASSIVE_LABEL_COLOR = 0xFF404040;
	private static final int PASSIVE_COOLDOWN_COLOR = 0xFF505050;
	private static final int LOCKED_COLOR = 0xB0B0B0;
	// the action HUD's cooldown fill over an icon
	private static final int COOLDOWN_OVERLAY_COLOR = 0x7FFFFFFF;

	// the abilities of the Stand's moveset marked passive, in the moveset's order
	public static List<Ability> passivesOf(@Nullable StandPower standPower) {
		if (standPower == null || !standPower.hasPower() || standPower.getMoveset() == null) {
			return List.of();
		}
		return standPower.getMoveset().abilities.values().stream().filter(Ability::isPassive).toList();
	}

	/*
	 * Under the skills: a line, the label and a row per passive skill, its icon (grey while it is locked, filled from the
	 * top for its cooldown as on the action HUD) and a check mark when it is ready, the seconds of its cooldown, or a dash
	 * while it is locked. Returns the label's scale when the label is cut by the list's edge even at the smallest size,
	 * so that hovering it shows it whole; else 0.
	 */
	private float renderPassives(GuiGraphics guiGraphics, int skillListX, int skillListY, AbilityIconSprites icons,
			float partialTick) {
		if (passives.isEmpty()) {
			return 0;
		}
		int skillCount = Iterables.size(skills);
		int headerY = skillListY + SkillListLayout.passiveHeaderTop(skillCount);
		guiGraphics.fill(skillListX + 2, headerY + 2, skillListX + 2 + SkillListLayout.LABEL_ROOM, headerY + 3, PASSIVE_LINE_COLOR);
		Component label = Component.translatable(PASSIVES_KEY);
		int labelWidth = font.width(label);
		float scale = TextBoxFit.labelScale(labelWidth, SkillListLayout.LABEL_ROOM);
		guiGraphics.pose().pushPose();
		guiGraphics.pose().translate(skillListX + 2, headerY + 5, 0);
		guiGraphics.pose().scale(scale, scale, 1);
		guiGraphics.drawString(font, label, 0, 0, PASSIVE_LABEL_COLOR, false);
		guiGraphics.pose().popPose();

		for (int i = 0; i < passives.size(); i++) {
			Ability passive = passives.get(i);
			int spriteX = skillListX + 2;
			int spriteY = skillListY + SkillListLayout.passiveRowTop(skillCount, i) + SkillListLayout.ROW_GAP;
			boolean unlocked = passive.isAbilityUnlocked(standPower);
			TextureAtlasSprite icon = icons.getAbilityIcon(passive, standPower, standSkin);
			RenderSystem.enableBlend();
			RenderSystem.defaultBlendFunc();
			BlitFloat.blit(guiGraphics.pose(), minecraft, icon, spriteX, spriteY, 16, 16, 0,
					unlocked ? BlitFloat.NO_TINT : 0x40404040);
			float ratio = standPower.getAbilityCooldownRatio(passive.name(), partialTick);
			if (ratio > 0) {
				int fillTop = spriteY + Math.round(16 * (1 - ratio));
				guiGraphics.fill(spriteX, fillTop, spriteX + 16, spriteY + 16, COOLDOWN_OVERLAY_COLOR);
				RenderSystem.enableBlend();
				RenderSystem.defaultBlendFunc();
			}
			int cooldown = standPower.getAbilityCooldown(passive.name());
			if (!unlocked) {
				guiGraphics.drawString(font, "-", spriteX + 18, spriteY + 4, LOCKED_COLOR, false);
			}
			else if (cooldown > 0) {
				guiGraphics.drawString(font, Component.translatable("jojo_ripples.stand_skills.passive.seconds",
						SkillListLayout.secondsShown(cooldown)), spriteX + 18, spriteY + 4, PASSIVE_COOLDOWN_COLOR, false);
			}
			else {
				guiGraphics.drawString(font, String.valueOf(IconSymbols.CHECKMARK), spriteX + 18, spriteY + 4, 0xFFFFFFFF);
			}
		}
		return labelWidth * scale > SkillListLayout.LABEL_ROOM ? scale : 0;
	}

	private boolean isOverPassivesLabel(double mouseX, double mouseY) {
		int x = getWindowX(this) + SKILL_LIST_X;
		int y = getWindowY(this) + SKILL_LIST_Y;
		if (passives.isEmpty() || mouseX < x || mouseX > x + 2 + SkillListLayout.LABEL_ROOM) {
			return false;
		}
		int pos = skillListScrolling.getYHovered(y, (int) mouseY);
		int top = SkillListLayout.passiveHeaderTop(Iterables.size(skills));
		return pos >= top && pos < top + SkillListLayout.PASSIVE_HEADER;
	}

	// the controls box of a passive skill: that it takes no key, then whether it is locked, cooling down or ready
	private void updatePassiveState(float partialTick) {
		if (selectedPassive == null) {
			return;
		}
		MutableComponent state = Component.translatable("jojo_ripples.stand_skills.passive").append("\n");
		ConditionCheck unlock = selectedPassive.getUnlockConditionCheck(standPower);
		int cooldown = standPower.getAbilityCooldown(selectedPassive.name());
		if (!unlock.isPositive()) {
			Component warning = unlock.getWarning();
			state.append(warning != null ? warning : Component.translatable("jojo.message.action_condition.not_unlocked"));
		}
		else if (cooldown > 0) {
			state.append(Component.translatable("jojo_ripples.stand_skills.passive.cooldown",
					String.format(java.util.Locale.ROOT, "%.1f", cooldown / 20.0F)));
		}
		else {
			state.append(Component.translatable("jojo_ripples.stand_skills.passive.ready"));
		}
		String shown = state.getString();
		if (!shown.equals(passiveStateShown)) {
			passiveStateShown = shown;
			setFittedText(skillControls, state, CONTROLS_TEXT_WIDTH);
		}
	}

	@Nullable
	protected Ability getHoveredPassive(double mouseX, double mouseY) {
		int x = getWindowX(this) + SKILL_LIST_X;
		int y = getWindowY(this) + SKILL_LIST_Y;
		if (mouseX >= x && mouseX <= x + 48) {
			int index = SkillListLayout.passiveAt(skillListScrolling.getYHovered(y, (int) mouseY), Iterables.size(skills),
					passives.size());
			if (index >= 0) {
				return passives.get(index);
			}
		}
		return null;
	}

	public void setSelectedPassive(@Nullable Ability passive) {
		if (passive == null) {
			setSelectedSkill(null);
			return;
		}
		setSelectedSkill(null);
		selectedPassive = passive;
		passiveStateShown = null;
		setFittedText(skillDescription, StandSkillText.passiveDesc(standPower, standSkin, passive), DESC_TEXT_WIDTH);
		updatePassiveState(0);
	}
	protected Component skinSkillName(UnlockableSkill skill) {
		return StandSkillText.name(standPower, standSkin, skill);
	}
	
	protected Component skinSkillDesc(UnlockableSkill skill) {
		return StandSkillText.desc(standPower, standSkin, skill);
	}
	
	protected Component skinSkillControls(UnlockableSkill skill) {
		return StandSkillText.controls(standPower, standSkin, skill);
	}

	@Nullable
	protected TrainableAbility getSelectedTrainableAbility() {
		return selectedSkill != null ? getTrainableAbility(selectedSkill) : null;
	}

	@Nullable
	protected TrainableAbility getTrainableAbility(StandUnlockableSkill skill) {
		if (standPower == null || standPower.getMoveset() == null || skill == null) {
			return null;
		}
		Set<String> abilityNames = new LinkedHashSet<>();
		abilityNames.add(skill.skillName);
		abilityNames.addAll(skill.unlocksAbilities);
		for (String abilityName : abilityNames) {
			Ability ability = standPower.getMoveset().getAbility(abilityName);
			if (ability instanceof TrainableAbility trainable
					&& trainable.getLearningAbilityName() != null
					&& trainable.getMaxTrainingPoints(standPower) > 0.0F) {
				return trainable;
			}
		}
		return null;
	}

	protected boolean isTrainingComplete(TrainableAbility trainable) {
		String learningAbilityName = trainable.getLearningAbilityName();
		if (learningAbilityName == null) {
			return true;
		}
		float maxTraining = trainable.getMaxTrainingPoints(standPower);
		return levelingData.getAbilityLearningProgressPoints(learningAbilityName) >= maxTraining;
	}

	protected void updateSelectedSkillText(@Nullable TrainableAbility trainable) {
		if (selectedSkill == null) {
			return;
		}
		Component desc = skinSkillDesc(selectedSkill);
		if (trainable != null && levelingData.isSkillUnlocked(selectedSkill.skillName)) {
			desc = desc.copy().append("\n\n").append(trainingProgressText(trainable));
		}
		setFittedText(skillDescription, desc, DESC_TEXT_WIDTH);
	}

	// a text with more lines than its box holds whole is drawn smaller rather than cut at the box's bottom
	private void setFittedText(ScrollingText box, Component text, int textWidth) {
		float scale = TextBoxFit.scaleToFit(splitWidth -> font.split(text, splitWidth).size(),
				textWidth, box.height, box.lineHeight, box.yOffset);
		box.setText(font.split(text, TextBoxFit.splitWidth(textWidth, scale)), scale);
	}

	// a long name is cut with an ellipsis before the header's cross and Stand EXP box; hovering it shows it whole
	private FormattedCharSequence fitTitle(Component name, int maxWidth) {
		if (font.width(name) <= maxWidth) {
			return name.getVisualOrderText();
		}
		FormattedText cut = font.substrByWidth(name, maxWidth - font.width(CommonComponents.ELLIPSIS));
		return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
	}

	protected Component trainingProgressText(TrainableAbility trainable) {
		String learningAbilityName = trainable.getLearningAbilityName();
		float maxTraining = trainable.getMaxTrainingPoints(standPower);
		float points = learningAbilityName != null ? Math.max(0.0F, levelingData.getAbilityLearningProgressPoints(learningAbilityName)) : 0.0F;
		int percent = maxTraining > 0 ? Math.min(100, Math.round(Math.min(points, maxTraining) * 100.0F / maxTraining)) : 100;
		return Component.translatable("jojo_ripples.stand_skills.training_progress", percent);
	}
	
	protected int getExpCostColor(StandUnlockableSkill skill) {
		ConditionCheck check = unlockSkillChecks.get(skill.skillName);
		if (check != null && (check.isPositive() || check == StandUnlockableSkill.NOT_ENOUGH_EXP)) {
			return STAND_EXP_NUMBER_COLOR;
		}
		else {
			return 0xB0B0B0;
		}
	}
	
	@Nullable
	protected StandUnlockableSkill getHoveredSkill(double mouseX, double mouseY) {
		int x = getWindowX(this) + SKILL_LIST_X;
		int y = getWindowY(this) + SKILL_LIST_Y;
		if (mouseX >= x && mouseX <= x + 48) {
			int index = SkillListLayout.skillAt(skillListScrolling.getYHovered(y, (int) mouseY), Iterables.size(skills));
			if (index >= 0) {
				return Iterables.get(skills, index);
			}
		}
		return null;
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (clickTab(mouseX, mouseY, button, this)) return true;
		StandUnlockableSkill skill = getHoveredSkill(mouseX, mouseY);
		if (skill != null) {
			setSelectedSkill(skill);
			return true;
		}
		Ability passive = getHoveredPassive(mouseX, mouseY);
		if (passive != null) {
			setSelectedPassive(passive);
			return true;
		}
		return super.mouseClicked(mouseX, mouseY, button);
	}
	
	public void setSelectedSkill(StandUnlockableSkill skill) {
		if (this.selectedSkill != skill || selectedPassive != null) {
			skillDescription.scrolling.setScrollOffset(0);
			skillControls.scrolling.setScrollOffset(0);
		}
		this.selectedSkill = skill;
		selectedPassive = null;
		passiveStateShown = null;
		if (skill != null) {
			updateSelectedSkillText(getSelectedTrainableAbility());
			setFittedText(skillControls, skinSkillControls(selectedSkill), CONTROLS_TEXT_WIDTH);
		}
		else {
			skillDescription.setText(null);
			skillControls.setText(null);
		}
		learnSkillButton.visible = skill != null;
	}

	@Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (
				skillDescription.mouseScrolled(mouseX, mouseY, scrollX, scrollY) || 
				skillControls.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
			return true;
		}
    	skillListScrolling.scroll(scrollY);
    	return true;
    }

}
