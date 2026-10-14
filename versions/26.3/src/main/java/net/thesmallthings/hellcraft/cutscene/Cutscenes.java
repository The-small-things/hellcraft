package net.thesmallthings.hellcraft.cutscene;

import net.thesmallthings.hellcraft.entity.boss.gen.MinosAnim;
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

	private static final List<Cutscene> ALL = List.of(MINOS_INTRO);

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
