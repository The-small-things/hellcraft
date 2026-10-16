package net.thesmallthings.hellcraft.cutscene;

import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FarinataAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FuryAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.GeryonAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.FallenSeraphAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.EmperorAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinotaurAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PhlegyasAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.PlutusAnim;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Every cutscene, by id. Their timings follow the boss animations they film (entity/boss/gen). */
public final class Cutscenes {
	private Cutscenes() {
	}

	private static final int VIRGIL = 0xE8C060;
	private static final int MINOS = 0xC060E0;

	/**
	 * Minos rises out of his coils, roars and names himself. Filmed from in front of him (the anchor is his body,
	 * facing whoever woke him): wide and low on the coils, pushing in as he rises, up at his face for the roar,
	 * then round to his side as his name comes up. Virgil says, plainly, how to read the fight.
	 */
	public static final Cutscene MINOS_INTRO = Cutscene.builder("minos_intro", MinosAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -7, 1.5, 15, 0, 1.5, 0)
			.shot(20, -6, 2.0, 13.5, 0, 2.2, 0)
			.shot(52, -3.5, 3.5, 10, 0, 4.2, 0)
			.shot(62, -2, 3.0, 7.5, 0, 6.2, 0)
			.shot(88, -1.5, 3.2, 7.0, 0, 6.4, 0)
			.shot(104, 2.5, 4.5, 11, 0, 5.0, 0)
			.shot(120, 4, 5.5, 14, 0, 4.5, 0)
			.caption(8, 58, "Virgil", VIRGIL, "Minos, the judge of the dead. Watch his colours: white, raise your shield; purple, jump; red, run.")
			.caption(MinosAnim.INTRO.at("roar") + 2, 116, "Minos", MINOS, "O thou who comest to the dolorous hospice, beware how thou enterest!")
			.shake(MinosAnim.INTRO.at("roar"), MinosAnim.INTRO.at("roar") + 24, 1.0f)
			.card(MinosAnim.INTRO.at("roar") + 4, 118, "MINOS", "JUDGE OF THE DAMNED", MINOS)
			.build();

	private static final int CERBERUS = 0x7A9A3A;

	/**
	 * Cerberus heaves himself out of the mud of his lair, shakes it off and roars with all three throats. Filmed low
	 * over the mud in front of him, pulling back as he rises, then up between the heads for the roar, and round to his
	 * flank as his name comes up. Virgil says how to read the fight.
	 */
	public static final Cutscene CERBERUS_INTRO = Cutscene.builder("cerberus_intro", CerberusAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -3, 1.0, 9, 0, 0.8, 0)
			.shot(16, -4, 1.4, 10, 0, 1.4, 0)
			.shot(56, -6, 2.5, 13, 0, 2.6, 0)
			.shot(80, -2, 3.0, 10, 0, 3.4, 0)
			.shot(92, -1, 3.5, 8.5, 0, 3.8, 0)
			.shot(104, 6, 3.5, 9, 0, 2.6, 0)
			.shot(120, 9, 4.0, 7, 0, 2.4, 0)
			.caption(8, 70, "Virgil", VIRGIL, "Cerberus, the great worm. Each head bites on its own: white, block it; red, get out; purple, keep away.")
			.caption(CerberusAnim.INTRO.at("roar") + 2, 116, "Cerberus", CERBERUS, "GRRRRAAAAAAAH!")
			.shake(CerberusAnim.INTRO.at("roar"), CerberusAnim.INTRO.at("roar") + 24, 1.2f)
			.card(CerberusAnim.INTRO.at("roar") + 4, 118, "CERBERUS", "THE GREAT WORM", CERBERUS)
			.build();

	private static final int PLUTUS = 0xE8B923;

	/**
	 * Plutus, slumped over his hoard, lifts his wolf's head, heaves himself up and shrieks. Filmed low past his fists,
	 * rising with him, up at his face for the shriek, and round to his flank as his name comes up.
	 */
	public static final Cutscene PLUTUS_INTRO = Cutscene.builder("plutus_intro", PlutusAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -4, 1.5, 10, 0, 1.8, 0)
			.shot(16, -5, 2.0, 11, 0, 2.6, 0)
			.shot(52, -3, 4.0, 11, 0, 4.6, 0)
			.shot(64, -1.5, 5.0, 8.5, 0, 6.0, 0)
			.shot(90, -1, 5.2, 8.0, 0, 6.2, 0)
			.shot(104, 6, 5.0, 10, 0, 4.6, 0)
			.shot(120, 9, 5.5, 8, 0, 4.2, 0)
			.caption(8, 64, "Virgil", VIRGIL, "Plutus, the old god of riches. Leave your gold behind: his gold rain hits harder the more you carry.")
			.caption(PlutusAnim.INTRO.at("roar") + 2, 116, "Plutus", PLUTUS, "Pape Satàn, pape Satàn aleppe!")
			.shake(PlutusAnim.INTRO.at("roar"), PlutusAnim.INTRO.at("roar") + 24, 1.0f)
			.card(PlutusAnim.INTRO.at("roar") + 4, 118, "PLUTUS", "THE GREAT ENEMY", PLUTUS)
			.build();

	private static final int PHLEGYAS = 0x4AA8B0;

	/**
	 * Phlegyas rises out of the pool at the heart of his landing, shakes the weed from his face and roars, his oar held
	 * high. Filmed low over the water in front of him, tilting up as he rises, and round to his flank for his name.
	 */
	public static final Cutscene PHLEGYAS_INTRO = Cutscene.builder("phlegyas_intro", PhlegyasAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -4, 1.0, 12, 0, 1.0, 0)
			.shot(16, -5, 1.5, 13, 0, 2.0, 0)
			.shot(60, -4, 4.0, 13, 0, 5.5, 0)
			.shot(84, -2, 5.0, 11, 0, 7.5, 0)
			.shot(96, -1, 5.5, 10, 0, 8.0, 0)
			.shot(106, 7, 5.5, 11, 0, 6.5, 0)
			.shot(120, 10, 6.0, 9, 0, 6.0, 0)
			.caption(8, 70, "Virgil", VIRGIL, "Phlegyas, the ferryman. Stay on the planks: white, block his oar; purple, jump it; red, get out.")
			.caption(PhlegyasAnim.INTRO.at("roar") + 2, 116, "Phlegyas", PHLEGYAS, "Now art thou arrived, fell soul!")
			.shake(PhlegyasAnim.INTRO.at("roar"), PhlegyasAnim.INTRO.at("roar") + 24, 1.0f)
			.card(PhlegyasAnim.INTRO.at("roar") + 4, 118, "PHLEGYAS", "THE FERRYMAN OF THE STYX", PHLEGYAS)
			.build();

	private static final int FURIES = 0xC0303A;

	/**
	 * The Furies rise over the battlements of Dis, tearing at their breasts, and scream for Medusa. Filmed low from the
	 * road, tilting up as they rise, close on the leader's face for the scream, and wide as their name comes up.
	 */
	public static final Cutscene FURIES_INTRO = Cutscene.builder("furies_intro", FuryAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -3, 0.5, 11, 0, 1.0, 0)
			.shot(FuryAnim.INTRO.at("rise"), -4, 1.0, 12, 0, 2.0, 0)
			.shot(FuryAnim.INTRO.at("scream") - 6, -1.5, 2.4, 6.5, 0, 2.6, 0)
			.shot(FuryAnim.INTRO.at("scream") + 12, -1.2, 2.5, 6.0, 0, 2.6, 0)
			.shot(FuryAnim.INTRO.at("scream") + 24, 6, 3.0, 12, 0, 2.2, 0)
			.caption(8, 42, "Virgil", VIRGIL, "The Furies. Three of them, one life between them: hit any. White, block; purple, keep away; red, move.")
			.caption(FuryAnim.INTRO.at("scream") + 2, 78, "The Furies", FURIES, "Let Medusa come, and we will make him stone!")
			.shake(FuryAnim.INTRO.at("scream"), FuryAnim.INTRO.at("scream") + 20, 0.8f)
			.card(FuryAnim.INTRO.at("scream") + 4, 78, "THE FURIES", "THE ERINYES OF DIS", FURIES)
			.build();

	private static final int FARINATA = 0xE8A040;

	/**
	 * Farinata rises from his burning tomb "from the girdle upward", looks you over with scorn and speaks. Filmed low
	 * past the tomb's lid, rising with him, close on his face as he speaks, then round to his side for his name.
	 */
	public static final Cutscene FARINATA_INTRO = Cutscene.builder("farinata_intro", FarinataAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -3, 0.8, 8, 0, 1.0, 0)
			.shot(FarinataAnim.INTRO.at("rise"), -3.5, 1.2, 9, 0, 1.8, 0)
			.shot(FarinataAnim.INTRO.at("speak") - 8, -1.2, 3.2, 5.0, 0, 3.4, 0)
			.shot(FarinataAnim.INTRO.at("speak") + 14, -1.0, 3.3, 4.6, 0, 3.4, 0)
			.shot(FarinataAnim.INTRO.at("speak") + 30, 5, 3.5, 8, 0, 2.8, 0)
			.caption(8, 64, "Virgil", VIRGIL, "Farinata, who scorns Hell itself. Watch the tombs: they burn in turn. Stand by a cold one.")
			.caption(FarinataAnim.INTRO.at("speak") + 2, 98, "Farinata", FARINATA, "Who were thine ancestors?")
			.card(FarinataAnim.INTRO.at("speak") + 4, 98, "FARINATA", "THE PROUD HERETIC", FARINATA)
			.build();

	private static final int MINOTAUR = 0xA04A2A;

	/**
	 * The Minotaur lies across the broken slope; he heaves himself up, paws the rubble and bellows. Filmed low past his
	 * horns, pulling back as he rises, up at his face for the bellow, then round to his flank for his name.
	 */
	public static final Cutscene MINOTAUR_INTRO = Cutscene.builder("minotaur_intro", MinotaurAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -3, 1.0, 9, 0, 1.0, 0)
			.shot(MinotaurAnim.INTRO.at("rise") - 10, -4, 1.4, 10, 0, 1.6, 0)
			.shot(MinotaurAnim.INTRO.at("rise") + 10, -5, 2.5, 12, 0, 3.6, 0)
			.shot(MinotaurAnim.INTRO.at("roar") - 4, -2, 3.8, 8.5, 0, 4.6, 0)
			.shot(MinotaurAnim.INTRO.at("roar") + 20, -1.5, 4.0, 8.0, 0, 4.8, 0)
			.shot(MinotaurAnim.INTRO.at("roar") + 40, 7, 4.0, 11, 0, 3.4, 0)
			.caption(8, MinotaurAnim.INTRO.at("roar") - 2, "Virgil", VIRGIL, "The Minotaur. When he lowers his horns and the line goes red, step aside: "
					+ "if he hits a wall, he's stunned.")
			.caption(MinotaurAnim.INTRO.at("roar") + 2, 98, "The Minotaur", MINOTAUR, "RRRRAAAAAWWWGH!")
			.shake(MinotaurAnim.INTRO.at("roar"), MinotaurAnim.INTRO.at("roar") + 24, 1.2f)
			.card(MinotaurAnim.INTRO.at("roar") + 4, 98, "THE MINOTAUR", "THE INFAMY OF CRETE", MINOTAUR)
			.build();

	private static final int GERYON = 0x3A6AB0;

	/**
	 * Geryon rises out of the abyss and lays his kindly face on the brink, his tail quivering in the void; then the
	 * face smiles too wide. Filmed from the brink, looking out and down as he rises, close on the face, then wide.
	 */
	public static final Cutscene GERYON_INTRO = Cutscene.builder("geryon_intro", GeryonAnim.INTRO.ticks)
			.fadeIn(10)
			.blendOut(12)
			.shot(0, -2, 3.0, 12, 0, -1.0, 0)
			.shot(GeryonAnim.INTRO.at("rise"), -3, 3.5, 13, 0, 1.0, 0)
			.shot(GeryonAnim.INTRO.at("rise") + 20, -2, 3.0, 10, 0, 2.4, 2)
			.shot(GeryonAnim.INTRO.at("roar") - 4, -1.5, 2.8, 8.0, 0, 2.6, 3)
			.shot(GeryonAnim.INTRO.at("roar") + 14, 5, 4.5, 12, 0, 2.0, 0)
			.caption(8, GeryonAnim.INTRO.at("roar") - 2, "Virgil", VIRGIL, "Geryon, the image of fraud. Don't trust the copies of him: hit one and "
					+ "it pops. Beat him and he'll fly you down.")
			.caption(GeryonAnim.INTRO.at("roar") + 2, 98, "Geryon", GERYON, "Peace, pilgrims. Come closer.")
			.card(GeryonAnim.INTRO.at("roar") + 4, 98, "GERYON", "THE IMAGE OF FRAUD", GERYON)
			.build();

	private static final int LUCIFER = 0xC02020;
	private static final int DANTE = 0xB0B0B0;

	/**
	 * The Fallen Seraph comes down out of the dark onto the ice, beautiful and calm, and lies. Filmed from the ice in
	 * front of where he lands (the anchor faces out of the pit, toward the players), looking up as he descends, close
	 * on his face as he speaks, then wide, with the black mountain of the pit behind him.
	 */
	public static final Cutscene LUCIFER_DESCENT = Cutscene.builder("lucifer_descent", FallenSeraphAnim.INTRO.ticks + 50)
			.fadeIn(20)
			.blendOut(16)
			.shot(0, 0, 2.0, 11, 0, 14, 0)
			.shot(FallenSeraphAnim.INTRO.at("land") - 10, 1.5, 2.2, 9, 0, 5, 0)
			.shot(FallenSeraphAnim.INTRO.at("land") + 4, 2, 2.6, 7, 0, 3.4, 0)
			.shot(FallenSeraphAnim.INTRO.at("speak") + 4, 0.8, 4.2, 4.2, 0, 4.4, 0)
			.shot(FallenSeraphAnim.INTRO.at("speak") + 40, 0.6, 4.3, 3.8, 0, 4.5, 0)
			.shot(FallenSeraphAnim.INTRO.ticks + 30, 5, 4, 13, 0, 9, -12)
			.caption(8, FallenSeraphAnim.INTRO.at("land") - 2, "Virgil", VIRGIL, "There he is: Lucifer, as he was before the Fall. Whatever he says, "
					+ "he is the enemy. Block his blade when it flashes white.")
			.caption(FallenSeraphAnim.INTRO.at("speak"), FallenSeraphAnim.INTRO.at("speak") + 30, "Lucifer", LUCIFER, "Peace. Lower thy blade, pilgrim.")
			.caption(FallenSeraphAnim.INTRO.at("speak") + 31, FallenSeraphAnim.INTRO.ticks + 26, "Lucifer", LUCIFER, "I am not thy enemy.")
			.card(FallenSeraphAnim.INTRO.ticks + 10, FallenSeraphAnim.INTRO.ticks + 48, "LUCIFER", "THE FALLEN SERAPH", LUCIFER)
			.build();

	/**
	 * The seraph's form breaks, and the camera pulls back and up from the shards to show what the pit's mountain is:
	 * him. The Emperor lifts his head and opens six eyes. Filmed in the Emperor's frame (forward is the way he faces).
	 */
	public static final Cutscene LUCIFER_REVEAL = Cutscene.builder("lucifer_reveal", EmperorAnim.REVEAL.ticks + 30)
			.fadeIn(6)
			.blendOut(20)
			.shot(0, 2, 4, 26, 0, 3, 22)
			.shot(25, 6, 9, 40, 0, 10, 10)
			.shot(EmperorAnim.REVEAL.at("open") - 5, 4, 16, 48, 0, 19, 0)
			.shot(EmperorAnim.REVEAL.at("open") + 12, 2, 20, 34, 0, 21, 0)
			.shot(EmperorAnim.REVEAL.at("roar") + 10, 14, 18, 58, 0, 24, 0)
			.shot(EmperorAnim.REVEAL.ticks + 28, 22, 16, 64, 0, 26, 0)
			.caption(6, 34, "Lucifer", LUCIFER, "...So be it. Thou wouldst see me as I am? LOOK DOWN.")
			.caption(36, EmperorAnim.REVEAL.at("roar") - 2, "Lucifer", LUCIFER, "The mountain thou hast been walking toward... was ME.")
			.caption(EmperorAnim.REVEAL.at("roar") + 24, EmperorAnim.REVEAL.ticks + 28, "Virgil", VIRGIL, "He can't leave the ice. When a hand or a face "
					+ "comes down, it glows: hit it then. Hide behind the pillars when his wings glow purple.")
			.shake(EmperorAnim.REVEAL.at("roar"), EmperorAnim.REVEAL.at("roar") + 30, 1.4f)
			.card(EmperorAnim.REVEAL.at("roar") + 4, EmperorAnim.REVEAL.at("roar") + 40, "LUCIFER", "THE THREE-FACED EMPEROR", LUCIFER)
			.build();

	/**
	 * The way out is through him (Inferno XXXIV): down his shaggy side, past the centre of the earth, where everything
	 * turns over (the camera rolls), and up toward the stars. Filmed in the Emperor's frame, down his left flank.
	 */
	public static final Cutscene LUCIFER_CLIMB = Cutscene.builder("lucifer_climb", 180)
			.fadeIn(10)
			.blendOut(1)
			.shot(0, -20, 14, -4, -2, 9, -2)
			.shot(45, -17, 6, -7, -2, 1, -5)
			.shot(80, -15, -2, -8, -2, -8, -6, 20)
			.shot(120, -15, -5, -8, -2, -12, -6, 180)
			.shot(179, -15, -4, -8, -2, -1, -6, 180)
			.caption(6, 60, "Virgil", VIRGIL, "Hold on to me. By such stairs as these we must leave so much evil behind.")
			.caption(62, 118, "Dante", DANTE, "We climbed down his shaggy side, between the hair and the crusts of ice...")
			.caption(120, 178, "Dante", DANTE, "...and where the thigh turns, I saw his legs held upward. Thence we came forth to see the stars again.")
			.build();

	private static final List<Cutscene> ALL = List.of(MINOS_INTRO, CERBERUS_INTRO, PLUTUS_INTRO, PHLEGYAS_INTRO, FURIES_INTRO, FARINATA_INTRO,
			MINOTAUR_INTRO, GERYON_INTRO, LUCIFER_DESCENT, LUCIFER_REVEAL, LUCIFER_CLIMB);

	@Nullable
	public static Cutscene byId(String id) {
		for (Cutscene c : ALL) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}
}
