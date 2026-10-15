package net.thesmallthings.hellcraft.cutscene;

import net.thesmallthings.hellcraft.entity.boss.gen.CerberusAnim;
import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
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

	private static final List<Cutscene> ALL = List.of(MINOS_INTRO, CERBERUS_INTRO, PLUTUS_INTRO, PHLEGYAS_INTRO);

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
