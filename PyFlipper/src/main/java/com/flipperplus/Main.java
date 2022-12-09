package com.flipperplus;

import com.flipperplus.commands.FlipperCommand;
import com.flipperplus.commands.subcommands.Help;
import com.flipperplus.commands.subcommands.Subcommand;
import com.flipperplus.commands.subcommands.Toggle;
import com.flipperplus.commands.subcommands.Token;
import com.flipperplus.events.OnChatReceived;
import com.flipperplus.events.OnTick;
import com.flipperplus.events.OnTooltip;
import com.flipperplus.events.OnWorldJoin;
import com.flipperplus.objects.AverageItem;
import com.flipperplus.utils.ApiHandler;
import com.flipperplus.utils.Utils;
import com.flipperplus.websocket.Client;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.ProgressManager;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Mod(modid = Reference.MOD_ID, name = Reference.NAME, version = Reference.VERSION)
public class Main {
    public static Config config = new Config();
    public static Authenticator authenticator;
    public static boolean checkedForUpdate = false;
    public static FlipperCommand commandManager = new FlipperCommand(new Subcommand[]{
        new Toggle(),
        new Help(),
        new Token()
    });
    public static Map<String, AverageItem> averageItemMap = new HashMap<>();
    public static Map<String, Date> processedItem = new HashMap<>(); // Date is the expiry time, indicates when the auction ends and should be purged to save memory for the long run
    public static Map<String, Integer> lbinItem = new HashMap<>();
    public static Map<String, Integer> bazaarItem = new HashMap<>(); // Long is the item's instant sell price
    public static Map<String, Integer> npcItem = new HashMap<>();
    public static List<String> chatFilters = new LinkedList<>();
    public static double balance = 0;
    public static boolean justPlayedASound = false; // This is to prevent multiple flips coming in at once and dinging the heck out of the user
    public static File jarFile;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        jarFile = event.getSourceFile();
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        ProgressManager.ProgressBar progressBar = ProgressManager.push("FlipperPlus", 4);
        authenticator = new Authenticator(progressBar);
        try {
            authenticator.authenticate(true);
        } catch (Exception e) {
            while (progressBar.getStep() < (progressBar.getSteps() - 1))
                progressBar.step("loading-failed-" + progressBar.getStep());
            e.printStackTrace();
            Reference.logger.error("FlipperPlus have been disabled due to an error while authenticating. Please check the logs for more information.");
            return;
        }
        progressBar.step("Registering events, commands, hooks & tasks");
        config.preload();
        ClientCommandHandler.instance.registerCommand(commandManager);
        MinecraftForge.EVENT_BUS.register(new OnWorldJoin());
        MinecraftForge.EVENT_BUS.register(new OnTick());
        MinecraftForge.EVENT_BUS.register(new OnTooltip());
        MinecraftForge.EVENT_BUS.register(new OnChatReceived());
        Tasks.updateBalance.start();
        Tasks.updateBazaarItem.start();
        Tasks.updateFilters.start();
        Utils.runInAThread(ApiHandler::updateNPC);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Reference.logger.info("Logging out...");
            try {
                authenticator.logout();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
        progressBar.step("Establishing WebSocket Connection");
        Client.connectWithToken();
        ProgressManager.pop(progressBar);
    }
}
