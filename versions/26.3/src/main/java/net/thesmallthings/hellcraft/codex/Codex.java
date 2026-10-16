package net.thesmallthings.hellcraft.codex;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The Codex (B2): one entry per boss and per monster of note, opened the first time you meet it (or when a shade of
 * Limbo hands you its page: Shades) and read in the Journal (J). Each says who it is, in two or three plain sentences
 * after Dante, how to fight it, and quotes the poem. The lore is optional: the fight's own tips say everything you
 * need.
 */
public final class Codex {
	private Codex() {
	}

	/** An entry: its title and subtitle, who it is, how to fight it, and a quote (with where it is from). */
	public record Entry(String id, String title, String subtitle, List<String> who, List<String> fight, String quote, String source) {
	}

	public static final Entry MINOS = new Entry("minos", "MINOS", "Judge of the Damned",
			List.of("Once a king of Crete, now the judge at the gate of the second circle. Every soul that comes down "
							+ "confesses to him, and he wraps his tail around himself once for each circle it must sink.",
					"He has judged since before Rome. He never asks whose law he keeps; the verdicts come up from below."),
			List.of("White flash, white ground: his claw. Raise your shield as it lands and he reels, his armour gone, for two seconds.",
					"Purple ring: his tail sweeps all the way round. Jump as it reaches you, or stand off past its tip.",
					"Red circle: a sentence. It follows you, stops, then the storm falls there. Keep moving.",
					"Red line: his coil. Step out of the line. If a friend is caught, hit him hard to free them.",
					"At half his health he sentences everyone at once, and attacks faster."),
			"There standeth Minos horribly, and snarls; examines the transgressions at the entrance; judges, and sends "
					+ "according as he girds him.",
			"Inferno V");

	public static final Entry CERBERUS = new Entry("cerberus", "CERBERUS", "the Great Worm",
			List.of("The three-headed hound of the old stories, grown into a worm of the mire. He guards the gluttons in the "
							+ "endless rain of the third circle, and barks over them until they wish they were deaf.",
					"Virgil quieted him once with a fistful of earth thrown into his throats. You will need more than that."),
			List.of("He hunts you round the lair, and the mud there swallows whoever stands still: keep moving.",
					"White flash: a head bites. Block it with your shield and that head reels.",
					"Red cone: all three maws at once, in front of him. Get behind him.",
					"Red puddles: his filth. Keep off them: they slow and sicken you.",
					"Purple ring: his howl. It can't be blocked: be out of its reach or be stunned.",
					"At half his health he rears up and slams the ground (a red ring), then charges along red lines. Step aside."),
			"Cerberus, a monster fierce and strange, with three gullets doglike barketh over the people that are there submerged.",
			"Inferno VI");

	public static final Entry PLUTUS = new Entry("plutus", "PLUTUS", "the Great Enemy",
			List.of("The old god of riches, swollen with all the gold of the fourth circle. He keeps the Mint where the "
							+ "misers and the spendthrifts roll their weights at each other forever.",
					"He shrieks words no one understands. Virgil's answer was to tell him to be silent, and he fell like a sail when the mast breaks."),
			List.of("Leave your gold behind before you face him: his gold rain hits harder the more you carry.",
					"Red circles: gold falls there. Step out.",
					"White flash: his fist. Block it and he reels.",
					"Red line: he hurls himself along it. Step aside.",
					"Purple ring: \"Pape Satàn\", a shriek that can't be blocked. Get out of the ring before it sounds.",
					"At half his health the Mint opens: great coins roll across the lair along red lines. Get off the lines."),
			"\"Pape Satàn, Pape Satàn aleppe!\" thus Plutus with his clucking voice began.",
			"Inferno VII");

	public static final Entry PHLEGYAS = new Entry("phlegyas", "PHLEGYAS", "the Ferryman of the Styx",
			List.of("A king who burned a god's temple in his rage, now the boatman of the fifth circle. A beacon on the "
							+ "watchtower calls him, and his ferry crosses the marsh to the burning walls of Dis.",
					"He thinks every soul he carries is one more of the wrathful, and treats them so."),
			List.of("Walk into the ring of braziers round his landing, or light both beacons on top of the east watchtower, and he rises. Fight him from the planks.",
					"White line: his oar. Block it as it lands and he reels.",
					"Purple ring: his oar sweeps the deck. It can't be blocked: jump it.",
					"Red ring: a wave washes over the deck's outer part. Get in close to him.",
					"Red circle: he reaches for you and throws you into the Styx. Keep moving; if you're thrown, swim clear of the bubbles.",
					"At half his health the sullen's hands burst up through the deck in red circles.",
					"Beat him and his ferry is yours: board it to cross to the Walls of Dis. At the other watchtowers the beacons call it too."),
			"\"Phlegyas, Phlegyas, thou criest out in vain for this once,\" my Lord said; \"longer thou shalt not have us than in passing o'er the mire.\"",
			"Inferno VIII");

	public static final Entry FURIES = new Entry("furies", "THE FURIES", "the Erinyes of Dis",
			List.of("Megaera, Alecto and Tisiphone, girt with green hydras, who keep the gate of the Iron City. They wheel over "
							+ "their lair just inside the wall of Dis and come down one at a time.",
					"Three bodies, one life: a blow to any of them counts. When they are hard pressed they call Medusa."),
			List.of("Walk into their lair just inside the south gate of Dis, and they rise over it.",
					"White circle that follows you: a Fury's talons. Block as she drops and she tumbles to the ground: hit her.",
					"Purple circle under a Fury: her shriek. It can't be blocked: get out from under her.",
					"Red line: her serpent hair cracks like a whip. Step aside.",
					"Red circles: tears of blood rain where you stand. Move.",
					"At half their life Medusa rises over the battlements. While her eyes glow, don't look at her: the screen greys as "
							+ "you start to turn to stone. Look away and you're fine."),
			"\"Let Medusa come, so we will make him stone,\" they all were saying, looking down.",
			"Inferno IX");

	public static final Entry FARINATA = new Entry("farinata", "FARINATA", "the Proud Heretic",
			List.of("Farinata degli Uberti, who drove his enemies out of Florence and denied the soul outlives the body. He "
							+ "stands in his burning tomb in the middle of the Iron City as if he held Hell in great disdain.",
					"He never leaves his tomb. Eight more tombs stand round it, and they burn at his word."),
			List.of("Walk into his lair among the tombs, west in Heresy, and he rises.",
					"White line: his fist strikes the rim and fire runs at you. Block it and he sinks into his tomb: hit him.",
					"Red circles on the tombs: they blast fire in patterns (every other one, round the ring, or all but one). "
							+ "Stand by a tomb that isn't marked.",
					"Purple ring round his tomb: the air blasts outward. It can't be blocked: stay out of the ring.",
					"At half his health the tombs fire twice for every command."),
			"He rose upright with his breast and with his front, as if he had Hell in great disdain.",
			"Inferno X");

	public static final Entry MINOTAUR = new Entry("minotaur", "THE MINOTAUR", "the Infamy of Crete",
			List.of("The bull-headed son of Pasiphaë, who ate the youths of Athens in the Labyrinth until Theseus killed him. "
							+ "He guards the dead wood beyond the river of blood, mad with rage.",
					"Like a bull that breaks loose when it has had its death blow, he plunges this way and that."),
			List.of("Walk into his lair in a clearing of the Wood of Suicides, to the north, and he gets up.",
					"White wedge in front of him: his horns. Block the thrust and he drops to one knee: hit him.",
					"Red line: he paws the ground and charges along it. Step aside. If he runs into rock he stuns himself.",
					"Red circle round him: he stamps. Get away from him.",
					"Purple ring round him: he whirls with his fists out. It can't be blocked: jump it or keep out of the ring.",
					"At half his health he charges twice in a row."),
			"And when he saw us, he bit himself, like one whom anger inwardly consumes.",
			"Inferno XII");

	public static final Entry GERYON = new Entry("geryon", "GERYON", "the Image of Fraud",
			List.of("The face of a just man, kindly to look at, on the painted body of a serpent with a scorpion's sting. He "
							+ "swims in the air at the brink of the great cliff down to Malebolge.",
					"Beaten, he bows his kindly head: from then on he flies anyone down the cliff, as he carried Dante and Virgil."),
			List.of("Walk to his lair at the brink of the cliff, the inner edge of the Burning Sands to the north-east, and he rises out of the abyss.",
					"White circle that follows you: his sting. Block it as it strikes and he sags to the ground: hit him.",
					"Purple lines: he and two false copies of him dive along them. They can't be blocked: get off the lines. One "
							+ "blow pops a copy.",
					"Red ring round the edge of the lair: his coils squeeze. Get to the middle.",
					"Red line: he swoops low across the lair. Step aside.",
					"At half his health venom drips where you stand (red circles). Keep moving.",
					"Beat him and right-click him at the brink: he flies you down over the first ditches to the fourth ridge of Malebolge, and back up."),
			"Behold the monster with the pointed tail, who crosses mountains, and breaks walls and weapons; behold the one who all the world doth stink!",
			"Inferno XVII");

	public static final Entry VULCAN = new Entry("vulcan", "VULCAN", "Smith of the Gods",
			List.of("The lame god of the forge, who made the thunderbolts Jove hurled at the giants. Dante hears the giant Capaneus "
							+ "dare Jove to weary all his smiths at the black forge of Mongibello: here, under Hell, that forge is the Great Forge of Dis.",
					"He made the gods' weapons, and he guards the secret of the Hellforge: beat him and its fire is yours to use."),
			List.of("White flash, white ground: his hammer comes down in front of him. Block it as it lands and he goes down on one knee.",
					"Red circle, then a purple ring: he leaps and lands on the circle; the shockwave rolls out across the floor. Get out of the "
							+ "circle, then jump the wave.",
					"Red circles round each of you: molten iron pours there. Move.",
					"Red line: his chain flies along it and drags whoever it catches to his feet. Step off the line.",
					"He calls smiths out of the fire: kill them before they surround you.",
					"At half his health he drives his hammer into the floor: the seams of the floor (red lines through the middle) burst "
							+ "between his blows."),
			"Then let him weary out the others, one by one, at the black forge of Mongibello.",
			"Inferno XIV");

	public static final Entry SERAPH = new Entry("seraph", "THE SERAPH", "Burning One of the Primum Mobile",
			List.of("Highest of the angels, burning nearest the point of light round which all the heavens turn. Isaiah saw them: six "
							+ "wings each, two to veil the face, two the feet, and two to fly.",
					"It guards the way from the Primum Mobile to the Empyrean, Paradiso's last trial. It does not die: beaten, it bows "
							+ "and rises into the light, gives each victor a Halo, and the column to the Empyrean opens."),
			List.of("White flash, white ground: its great wings sweep forward. Block them as they land and it sinks, reeling.",
					"Red circles round each of you: fire falls there. Move.",
					"A red circle on you: it throws the live coal, which bursts there. Get out of it.",
					"A red ring round it, everywhere but close beside it: the Gloria. Run IN, close to it, before it blazes.",
					"It calls down the Angelic Guard: kill them.",
					"At half its strength all six wings flare. From then on a purple ring means the turning heavens: a beam sweeps "
							+ "round the floor. Jump it as it passes."),
			"Above it stood the seraphim: each one had six wings; with twain he covered his face, and with twain he covered his "
					+ "feet, and with twain he did fly.",
			"Isaiah VI");

	public static final Entry LUCIFER = new Entry("lucifer", "LUCIFER", "Emperor of the Kingdom Dolorous",
			List.of("Once the fairest of the angels, he rose against his maker and fell. Where he struck the earth, Hell opened, "
							+ "and he stands frozen at its bottom to the chest in the ice of Cocytus, the wind of his six wings keeping it frozen.",
					"He meets you first as the Seraph he was before the Fall, beautiful and kind. It is a lie: underneath sleeps the "
							+ "giant with three faces, chewing the three worst traitors."),
			List.of("The Seraph. White flash: his blade. Block it and he reels. Purple ring: his wings sweep round; jump it or stand "
							+ "out of reach. Red circles: stars fall there. Red line: he lunges along it. At half his health he is faster.",
					"The Emperor. He can't be hurt anywhere but where he opens up. Red: a hand slams down; once it lands, hit the "
							+ "glowing hand. White flash: a face bites; block it and the face stays down, glowing, for longer, and takes "
							+ "more damage.",
					"Purple ring round the whole pit: his wingbeat. It can't be blocked: get behind one of the ice pillars before it comes.",
					"At half his health he weeps too: red circles of frozen tears. Keep moving.",
					"The wingbeat reaches all of Cocytus: if you feel it far from the pit, a lit brazier keeps you warm."),
			"The Emperor of the kingdom dolorous from his mid-breast forth issued from the ice.",
			"Inferno XXXIV");

	public static final Entry HOARDER = new Entry("hoarder", "THE HOARDERS", "the Misers of the Fourth Circle",
			List.of("The misers of Greed, bent under sacks of coin. In the poem they roll great weights against the "
							+ "spendthrifts forever; these have found something lighter to carry: other people's gold.",
					"They can smell gold through a pack, and they come for whoever carries the most."),
			List.of("In Greed, carrying gold (or diamonds, emeralds, netherite) draws them: the more you carry, the more come.",
					"Each blow steals some of your treasure. Kill the thief and it drops everything it took.",
					"Stash your gold (an ender chest keeps it anywhere) and they lose interest."),
			"Here saw I people, more than elsewhere, many, on one side and the other, with great howls, rolling weights forward by main force of chest.",
			"Inferno VII");

	public static final Entry LOVERS = new Entry("lovers", "THE LOVERS", "Souls of the Second Circle",
			List.of("Those who gave themselves to love above all else, blown forever on the hurricane of Lust, two by two. "
							+ "Dante meets a pair who will not be parted even here, and faints from pity.",
					"The wind carries them; they never touch the ground."),
			List.of("They circle over you, then dive. Their blow throws you hard, up and away: keep away from cliff edges.",
					"A bow or a well-timed swing as they come in brings them down."),
			"Love, that exempts no one beloved from loving, seized me with such delight in him, that, as thou seest, it doth not leave me yet.",
			"Inferno V");

	public static final Entry GLUTTON = new Entry("glutton", "THE GLUTTONS", "Souls of the Third Circle",
			List.of("The gluttons lie sunk in the filthy mire under the cold, heavy rain, swollen past their shape.",
					"They wait half-buried, and heave themselves up when something edible comes close."),
			List.of("A hump in the mud may be one: it rises when you come within a few blocks.",
					"Its belly slam leaves you starving and sick. Keep food at hand.",
					"It bursts into a cloud of filth when it dies: step out of it."),
			"The others lay all of them upon the ground, except a single one who sat up straight, as soon as he saw us passing.",
			"Inferno VI");

	public static final Entry WRATHFUL = new Entry("wrathful", "THE WRATHFUL", "Souls of the Fifth Circle",
			List.of("The wrathful brawl forever in the mud of the Styx, naked and furious, tearing at each other with hands, "
							+ "heads, chests and teeth.",
					"They will fight anything, each other included, and come in packs."),
			List.of("Hurt one and it flies into a rage: faster and harder for a few seconds. Back off, or finish it.",
					"Lead a pack into another and they may turn on each other."),
			"And they smote each other not only with the hand, but with the head and with the chest and with the feet, tearing each other piecemeal with their teeth.",
			"Inferno VII");

	public static final Entry HERETIC = new Entry("heretic", "THE HERETICS", "Souls of the Sixth Circle",
			List.of("Those who denied the soul lie in open tombs in the Iron City, burnt black by the fire inside.",
					"When a tomb blasts near you, one may climb out of it."),
			List.of("Its grip sets you alight. Fire can't hurt it; water and a good blade can.",
					"It can't do anything while it climbs out: hit it then."),
			"From within came forth such dire laments, that well they seemed to be of wretched souls and tormented.",
			"Inferno IX");

	public static final Entry CENTAUR = new Entry("centaur", "THE CENTAURS", "Archers of the River of Blood",
			List.of("The centaurs patrol the banks of the river of blood, shooting any soul that rises out of it further than its guilt allows.",
					"One stands on each bank beside every ford."),
			List.of("Each paces its bank, then stops and watches the river: the red cone on the ground is where it's looking.",
					"Anyone it sees in the river or on the ford gets burning arrows. Wait on the bank and cross while it looks away.",
					"Hurt one and it shoots back whatever it was doing."),
			"Round about the moat in thousands go, shooting with shafts whatever soul uplifts itself out of the blood more than its guilt allotted.",
			"Inferno XII");

	public static final Entry HARPY = new Entry("harpy", "THE HARPIES", "the Wood of the Suicides",
			List.of("Women's heads on great birds' bodies, the harpies roost in the dead trees of the suicides and feed on their leaves.",
					"They come when the trees scream."),
			List.of("Push through the branches or break them, and the trees scream: sneak through the Wood and they stay asleep.",
					"They wheel overhead, then stoop: their talons leave you slowed. Hit them as they come in."),
			"Broad wings have they, and human necks and faces, feet with claws, and their great belly feathered; they make lament upon the strange trees.",
			"Inferno XIII");

	public static final Entry MIMIC = new Entry("mimic", "THE MIMICS", "Lies of the Eighth Circle",
			List.of("In Malebolge nothing is what it seems. A third of the chests on the ridges between the ditches are mimics,",
					"waiting with their teeth shut for a hand to reach in."),
			List.of("The tell: now and then a mimic's lid lifts a crack and settles, as if breathing. Watch a chest for a few seconds before you open it.",
					"Come close, touch it or hit it and it wakes: it bites hard and hops after you. Killed, it gives up its treasure, better than a chest's."),
			"There is a place in Hell called Malebolge, wholly of stone and of an iron colour.",
			"Inferno XVIII");

	public static final Entry THIEF = new Entry("thief", "THE THIEVES", "Souls of the Seventh Ditch",
			List.of("The thieves run naked through Malebolge, their hands bound behind them with serpents.",
					"They snatch what they can and run."),
			List.of("A thief's grab takes a stack from your pack (never the item in your hand, nor your armour).",
					"It keeps what it took until it dies: chase it down. A bow helps."),
			"With serpents were their hands behind them bound, which through the loins thrust forward tail and head.",
			"Inferno XXIV");

	public static final Entry MALEBRANCHE = new Entry("malebranche", "THE MALEBRANCHE", "the Evil Claws of the Fifth Ditch",
			List.of("Winged devils with long hooks who keep the ditches of boiling pitch, where the grafters are sunk. Whoever "
							+ "shows above the pitch they rake under again, and they would gladly do the same to you.",
					"Their captain is Malacoda, Evil Tail. He's friendly, helpful, and he lies."),
			List.of("Red line from a devil to you: it is aiming its hook. Step off the line before it throws, or it drags you to its claws.",
					"Hit one while it aims and its throw is spoiled.",
					"If a devil offers to show you a better way, don't believe it. The road's bridges are the way down."),
			"\"Let not one of you be wicked!\" ... and he had made a trumpet of his rump.",
			"Inferno XXI");

	public static final Entry CYCLOPS = new Entry("cyclops", "THE CYCLOPES", "Smiths of the Quarries",
			List.of("One-eyed giants, Vulcan's smiths: under Etna they forged Jove's thunderbolts. In the Forge of Dis they cut stone in "
							+ "the quarries west of the Great Forge, and each keeps a camp by his fire.",
					"Leave a Cyclops alone and he stays by his fire. Come near and he wakes."),
			List.of("White flash: his maul. Block it with your shield as it lands and he goes down on one knee.",
					"Red circle: he tears up a boulder and throws it there. Move.",
					"Purple ring round him: his stamp. Jump as it comes, or keep out of the ring.",
					"At half his health he throws two boulders at a time. His camp's chest and his body hold gold, iron, and sometimes "
							+ "netherite scrap."),
			"Within, the Cyclopes were forging iron in the vast cave, Brontes and Steropes and naked-limbed Pyracmon.",
			"Aeneid VIII");

	public static final List<Entry> ALL = List.of(MINOS, CERBERUS, PLUTUS, PHLEGYAS, FURIES, FARINATA, MINOTAUR, GERYON, VULCAN, SERAPH, LUCIFER, HOARDER, LOVERS, GLUTTON, WRATHFUL, HERETIC, CENTAUR, HARPY,
			MIMIC, THIEF, MALEBRANCHE, CYCLOPS);

	@Nullable
	public static Entry byId(String id) {
		for (Entry e : ALL) {
			if (e.id().equals(id)) {
				return e;
			}
		}
		return null;
	}
}
