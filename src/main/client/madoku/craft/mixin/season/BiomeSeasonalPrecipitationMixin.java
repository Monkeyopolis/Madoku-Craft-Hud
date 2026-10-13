package madoku.craft.mixin.season;

import madoku.craft.java.core.season.SeasonClientAPIManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public abstract class BiomeSeasonalPrecipitationMixin {
	@Inject(
		method = "getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;",
		at = @At("HEAD"),
		cancellable = true
	)
	private void madoku$seasonalPrecipitationAtPosition(
		BlockPos pos,
		int seaLevel,
		CallbackInfoReturnable<Biome.Precipitation> cir
	) {
		if (!SeasonClientAPIManager.isSynchronized()
			|| !SeasonClientAPIManager.isPrecipitating()) {
			return;
		}
		Biome.Precipitation precipitation = SeasonClientAPIManager.resolve((Biome) (Object) this);
		cir.setReturnValue(precipitation);
	}
}
