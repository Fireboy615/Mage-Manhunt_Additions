package net.fireboy.mageadditions.spell;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

public final class CaptureManager {
    private static final double RANGE = 16.0;
    private static final int STRUGGLES_TO_ESCAPE = 14;
    private static final Map<UUID, Capture> BY_TARGET = new HashMap<>();
    private static final Map<UUID, UUID> BY_CASTER = new HashMap<>();
    private CaptureManager(){}

    public static void cast(LivingEntity caster, int spellLevel){
        if(!(caster instanceof ServerPlayer player)) return;
        UUID old = BY_CASTER.get(player.getUUID());
        if(old != null){ release(old, Component.translatable("message.mageadditions.capture.released_by_caster")); return; }
        LivingEntity target = findTarget(player);
        if(target == null){ player.sendSystemMessage(Component.translatable("message.mageadditions.capture.no_target").withStyle(ChatFormatting.RED)); return; }
        if(BY_TARGET.containsKey(target.getUUID())) return;
        boolean oldNoAi = target instanceof Mob mob && mob.isNoAi();
        if(target instanceof Mob mob) mob.setNoAi(true);
        Capture c = new Capture(player.getUUID(), target.getUUID(), target.position(), CaptureSpell.durationTicks(spellLevel), oldNoAi);
        BY_TARGET.put(target.getUUID(), c); BY_CASTER.put(player.getUUID(), target.getUUID());
        if (target instanceof ServerPlayer capturedPlayer) capturedPlayer.sendSystemMessage(Component.translatable("message.mageadditions.capture.captured_player"));
    }

    private static LivingEntity findTarget(ServerPlayer caster){
        Vec3 eye=caster.getEyePosition(), look=caster.getLookAngle().normalize(), end=eye.add(look.scale(RANGE));
        AABB box=caster.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(2.0);
        LivingEntity best=null; double bestScore=Double.MAX_VALUE;
        for(Entity e: caster.level().getEntities(caster, box, e -> e instanceof LivingEntity && e.isAlive())){
            LivingEntity le=(LivingEntity)e; Vec3 p=le.getBoundingBox().getCenter();
            Vec3 rel=p.subtract(eye); double along=rel.dot(look); if(along<0 || along>RANGE) continue;
            double side=rel.subtract(look.scale(along)).lengthSqr();
            double radius=Math.max(1.0, le.getBbWidth()*0.75+0.5); if(side>radius*radius) continue;
            double score=side*8.0+along; if(score<bestScore){bestScore=score; best=le;}
        }
        return best;
    }

    public static void struggle(ServerPlayer player){
        Capture c=BY_TARGET.get(player.getUUID()); if(c==null) return;
        c.struggles++;
        if(c.struggles>=STRUGGLES_TO_ESCAPE) release(player.getUUID(), Component.translatable("message.mageadditions.capture.escaped"));
    }

    public static boolean isCaptured(Entity e){ return e!=null && BY_TARGET.containsKey(e.getUUID()); }

    public static void onServerTick(ServerTickEvent.Post event){
        Iterator<Capture> it=BY_TARGET.values().iterator();
        while(it.hasNext()){
            Capture c=it.next(); LivingEntity target=find(event.getServer(), c.target);
            if(target==null || !target.isAlive() || --c.ticks<=0){ cleanup(event.getServer(), c, target); it.remove(); continue; }
            target.setDeltaMovement(Vec3.ZERO); target.fallDistance=0; target.teleportTo(c.pos.x,c.pos.y,c.pos.z);
        }
    }
    public static void onServerStopped(ServerStoppedEvent event){ BY_TARGET.clear(); BY_CASTER.clear(); }

    private static LivingEntity find(net.minecraft.server.MinecraftServer server, UUID id){
        ServerPlayer p=server.getPlayerList().getPlayer(id); if(p!=null) return p;
        for(var level: server.getAllLevels()){ Entity e=level.getEntity(id); if(e instanceof LivingEntity le) return le; }
        return null;
    }
    private static void release(UUID targetId, Component message){
        Capture c=BY_TARGET.remove(targetId); if(c==null) return; BY_CASTER.remove(c.caster);
        var server=net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer(); if(server==null) return;
        LivingEntity target=find(server,targetId); if(target instanceof Mob mob) mob.setNoAi(c.oldNoAi); if(target instanceof ServerPlayer capturedPlayer) capturedPlayer.sendSystemMessage(message);
    }
    private static void cleanup(net.minecraft.server.MinecraftServer server, Capture c, LivingEntity target){
        BY_CASTER.remove(c.caster); if(target instanceof Mob mob) mob.setNoAi(c.oldNoAi);
    }
    private static final class Capture{ final UUID caster,target; final Vec3 pos; int ticks,struggles; final boolean oldNoAi; Capture(UUID c,UUID t,Vec3 p,int ticks,boolean n){caster=c;target=t;pos=p;this.ticks=ticks;oldNoAi=n;} }
}
