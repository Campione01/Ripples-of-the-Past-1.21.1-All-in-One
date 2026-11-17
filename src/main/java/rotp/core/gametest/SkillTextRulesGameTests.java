package rotp.core.gametest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;

import rotp.core.core.JojoMod;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Skills screen texts that tell the controls: they name the keys the client fills in (StandSkillText) in each of the core's
 * twelve languages, in that language. How the screen lays them out is an in-game check.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SkillTextRulesGameTests {
	private static final List<String> LANGUAGES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br",
			"ru_ru", "uk_ua", "zh_cn", "zh_tw");

	private SkillTextRulesGameTests() {}

	/*
	 * OQ-C09: Use Item's description named Shift as keeping the button for other moves and the own hand; in play the grab and
	 * charged heavy key keeps them and the HUD-controls key gives the own hand. Both keys are rebindable: the text takes their
	 * names as arguments 2 and 3 and writes no key of its own.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void useItemTellsTheKeysThatKeepTheGrabAndTheOwnHand(GameTestHelper helper) {
		for (String language : LANGUAGES) {
			String desc = value(helper, language, "jojo_ripples.skill.item_use.desc");
			helper.assertTrue(count(desc, "%2$s") == 1 && count(desc, "%3$s") == 1 && !desc.contains("Shift"),
					language + "'s Use Item description does not name the grab key (%2$s) and the own-hand key (%3$s) once"
							+ " each, or writes a key of its own: " + desc);
		}
		String text = compact(source("client/ui/text/StandSkillText.java"));
		helper.assertTrue(text.contains("if(StandItemUse.SKILL.equals(skill.skillName)){")
				&& text.contains("\".desc\",VariationKeyName.get(),grabChargedHeavyKeyName(),ownHandKeyName());")
				&& text.contains("inputHandler.vanillaKeybinds.disableHUDControls"),
				"StandSkillText no longer gives Use Item's description the grab key and the HUD-controls key");
		helper.succeed();
	}

	/*
	 * OQ-C10: Hierophant Green's barrier description says how the net comes down (the variation press), and in each language
	 * the whole line is in that language; nine of them had kept the English first sentence.
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theBarrierTellsHowTheNetComesDownInEachLanguage(GameTestHelper helper) {
		String english = value(helper, "en_us", "jojo_ripples.skill.barrier.desc");
		String firstSentence = english.substring(0, english.indexOf('.') + 1);
		for (String language : LANGUAGES) {
			String desc = value(helper, language, "jojo_ripples.skill.barrier.desc");
			helper.assertTrue(count(desc, "%s") == 1, language + "'s barrier description does not name the variation key: " + desc);
			helper.assertTrue(language.startsWith("en_") || !desc.contains(firstSentence),
					language + "'s barrier description is partly English: " + desc);
		}
		helper.succeed();
	}

	/*
	 * Traditional Chinese in Taiwan writing: no value of zh_tw holds a character that exists only in Simplified Chinese, and
	 * none is left as en_us's English. Left alone: the lifeform lane's keys, scaffold keys no screen shows (Java names under
	 * "hamon.", "{...}" keys, "_comment" keys, keys ending in "_") and "PvP".
	 */
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void zhTwIsTraditionalChineseThroughout(GameTestHelper helper) {
		JsonObject tw = StandClipPoses116GameTests.readJson(helper, "lang/zh_tw.json");
		JsonObject en = StandClipPoses116GameTests.readJson(helper, "lang/en_us.json");
		List<String> simplified = new java.util.ArrayList<>();
		List<String> english = new java.util.ArrayList<>();
		for (String key : tw.keySet()) {
			String value = tw.get(key).getAsString();
			if (key.contains("lifeform") || key.startsWith("jojo_ripples.skill.lifeshot")
					|| isScaffoldKey(key, value) || key.equals("jojo_ripples.config.preset.pvp")) {
				continue;
			}
			if (value.codePoints().anyMatch(c -> SIMPLIFIED_ONLY.indexOf(c) >= 0)) {
				simplified.add(key + " = " + value);
			}
			else if (en.has(key) && en.get(key).getAsString().equals(value)
					&& value.replaceAll("%(\\d\\$)?s|\u00a7.", "").matches("(?s).*[A-Za-z]{3,}.*")) {
				english.add(key + " = " + value);
			}
		}
		helper.assertTrue(simplified.isEmpty(), "zh_tw holds Simplified Chinese: " + simplified);
		helper.assertTrue(english.isEmpty(), "zh_tw is left in English: " + english);
		helper.succeed();
	}

	private static boolean isScaffoldKey(String key, String value) {
		String last = key.substring(key.lastIndexOf('.') + 1);
		if (key.contains("{") || key.startsWith("_comment") || key.endsWith("_")) {
			return true;
		}
		return key.startsWith("hamon.") && value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "")
				.equals(last.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", ""));
	}

	// characters that exist only in Simplified Chinese; ones valid in Taiwan writing (干 并 里 后 面 只 台 余 准 制 范 系 斗 谷 表 通 划 岳) are left out
	private static final String SIMPLIFIED_ONLY = "们这说时会对发过还没个为东么吗车门问间长开关见觉认让议记设证评话该语请读谁调谈谢贵费资质赶边达运进远连适选递逻遗围图团园国圣场块坏坚执报扫护拥择换挥据掷摆击战户扑扩应广庆库废张弹归录彻态总恶惊惯愿义乐习书买乱亲亿仅从仓仪价众优伤伟传体佣侠侧侦俭债倾偿储兰兴养兽册冲决况冻净凉减凤处则刚创删别剂剑剧劝办务动励劲劳势勋区医华协单卖卫却厂厅历厉压厌县参双变叙叶号叹吓吕启听吴呜员响哑唤啰圆坛坝坟坠垄垒垦垫堕墙壮声壳备复够头夸夺奋奖妆妇妈娱婴孙学宁宝实宠审宪宫宽宾寻导寿将尘尝层属岁岂岗岛岭峡帅师帐带帮庄庙异弃弥弯强当彦径忆忧怀怜恋恳恼悦惧惨惩惫愤慑懒戏执扪扬扰抚抛抢担拟拢拣拦拧拨挂挚挛挟挠挡挣挤捞损捡捣掳掸掺揽搀搁搂搅携摄摇摊撑撵擞攒敌敛数斋斩断无旧旷昼显晋晒晓晕暂术机杀杂权条来杨极构枢枪柜栅标栈栋栏树样桥梦检楼榄横欢欧歼残毁毕毙气汇汉汤汹沟沥沦沧泪泻泼泽洁洒浅浇浊测济浑浓涛涡涣涤润涨涩渊渍渐渔渗湾湿溃溅滚滞满滤滥滨滩潜澜灭灯灵灾灿炉点炼烁烂烛烟烦烧烫热焕爱爷牵状犹狈狞独狭狮狰猎猪献环现玺琐电画畅疗疯痒瘫皱盏盐监盖盘着睁瞒矫矿码砖础确碍礼祸离种积称税稳穷窃窍窜窝竖竞笔笼筑筛筹签简篮类粮紧纠红纤约级纪纬纯纱纲纳纵纷纸纹纺纽线练组细织终绍经绑绒结绕绘给络绝统绢绣继绩绪续绳维绵绷绸综绽绿缀缓编缘缚缝缠缩缴网罗罚罢脏脑脸舱艰艳艺节芦苍苏苹茎荐荡荣荧药莲获莹营萝萤蓝蕴虏虑虚虫虽虾蚀蚁蚂蛮蝇补衬袄袜袭装裤观规觅视览触誉计订讥讨训讯讲讳讶许论讼访识诈诉诊词译试诗诚诞询详诬误诱诵诸诺课谅谊谋谍谎谐谓谜谣谦谨谬谱谴贝贞负贡财责贤败账货贩贪贫购贯贱贴贷贸贺贼资赋赌赎赏赐赔赖赚赛赞赠赢趋跃践踪踊轨转轮软轰轴轻载轿较辅辆辈辉辐辑输辖辞辩边辽迁迈违迟逊遥邓邮邻郑酱酿释钉针钓钙钝钞钟钢钥钩钮钱钳钻铁铃铅铜铭铲银铸铺链销锁锅锈锋锐错锡锤锦键锯锻镇镜闪闭闯闲闷闸闹闻阀阁阅阐阔队阳阴阵阶际陆陈险隐隶难雾静页顶项顺须顽顾顿颁颂预领颇颈频题颜额风飘飞饥饭饮饰饱饲饵饼馆驱驳驶驻驾骂骄验骑骗骚骤鱼鲁鲜鸟鸡鸣鸭鹰麦黄齐齿龄龙龟";

	private static String value(GameTestHelper helper, String language, String key) {
		JsonObject words = StandClipPoses116GameTests.readJson(helper, "lang/" + language + ".json");
		helper.assertTrue(words.has(key), language + " lacks " + key);
		return words.get(key).getAsString();
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int at = text.indexOf(part); at >= 0; at = text.indexOf(part, at + part.length())) {
			n++;
		}
		return n;
	}

	private static String compact(String source) {
		return source.replaceAll("\\s+", "");
	}

	private static String source(String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path);
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		throw new IllegalStateException("No source of " + file + " above " + Path.of("").toAbsolutePath());
	}
}
