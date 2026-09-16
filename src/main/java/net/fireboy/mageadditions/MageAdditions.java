package net.fireboy.mageadditions;

import com.mojang.logging.LogUtils;
import net.fireboy.mageadditions.command.ModCommands;
import net.fireboy.mageadditions.config.CastTimeOverrides;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MageAdditions.MODID)
public final class MageAdditions {
    public static final String MODID = "mageadditions";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MageAdditions() {
        CastTimeOverrides.reload();
        NeoForge.EVENT_BUS.addListener(ModCommands::register);

        LOGGER.info("Mage Additions loaded");
    }
}
