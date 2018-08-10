package scala.com.thomas.customworld.player

import com.boydti.fawe.FaweAPI
import com.sk89q.worldedit.regions.CuboidRegion

import scala.com.thomas.customworld.messaging.ErrorMsg
import scala.com.thomas.customworld.player.freeop.ProtectionFeature
import scala.com.thomas.customworld.util._
import org.bukkit.entity.Player
import org.bukkit.event.{Cancellable, Event}
import org.bukkit.event.block.{BlockBreakEvent, BlockEvent, BlockFadeEvent, BlockPlaceEvent}

import scala.com.thomas.customworld.player.{PlayerType, rank}
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.{Location, Material}
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.player.{PlayerInteractEntityEvent, PlayerInteractEvent, PlayerMoveEvent}
import org.bukkit.event.vehicle.VehicleMoveEvent
import org.bukkit.plugin.Plugin
import org.bukkit.util.Vector

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.com.thomas.customworld.db.BuildDB
import com.sk89q.worldguard.protection

import scala.com.thomas.customworld.player

package object freeop {
  case class ProtectedRegion (region:Box, owner:List[UUID])

  var protectedRegions:mutable.Set[ProtectedRegion] = mutable.Set()
  object jumppadSettings {
    var force:Double = 0
    var on = false

    def update(fileConfiguration: FileConfiguration): Unit = {
      force = fileConfiguration.getDouble("freeop.jumppad.force")
      on = fileConfiguration.getBoolean("freeop.jumppad.on")
    }
  }

  def registerProtected(c:ProtectedRegion): Unit = {
    protectedRegions += c //maybe add to config?
  }

  def unRegisterProtected (c:ProtectedRegion): Unit = {
    protectedRegions -= c
  }

  def isProtected (location: Location): Boolean = protectedRegions exists (_.region.hasXZ(location.getBlockX, location.getBlockZ))
  def isProtectedPlayer (xpos:Int, zpos:Int, bplayer: Player): Boolean = {
    protectedRegions exists (x => x.region.hasXZ(xpos, zpos) && (!(x.owner contains toUUID(bplayer.getUniqueId))))
  }
  def protect[T <: Cancellable] (e: T, player: Player): Unit = {
    val loc = e match {case e:BlockEvent => e.getBlock.getLocation(); case e:PlayerInteractEvent => if (e.hasBlock) e.getClickedBlock.getLocation() else e.getPlayer.getLocation; case e:PlayerInteractEntityEvent => e.getRightClicked.getLocation()}
    if (!(!isProtectedPlayer(loc.getBlockX, loc.getBlockZ, player) || player.hasPermission("spawnbuild"))) {
      ErrorMsg ("tooclose") sendClient player
      e.setCancelled (true)
    }
  }

  def jumppad(event: Event): Unit = {
    if (jumppadSettings.on) {
      val to = event match {
        case e: VehicleMoveEvent => e.getTo
        case e: PlayerMoveEvent => e.getTo
      }
      val entity = event match {
        case e: VehicleMoveEvent => e.getVehicle
        case e: PlayerMoveEvent => e.getPlayer
      }

      if (to.clone().subtract(0, 1, 0).getBlock.getType == Material.EMERALD_BLOCK) {
        val vel = entity.getVelocity.clone()
        entity.setVelocity(new Vector(vel.getX*3, Math.abs(vel.getY)+jumppadSettings.force, vel.getZ*3))
      }
    }
  }

  case class FreeOPPlayer(cfg:FileConfiguration) extends PlayerType {
    override def extraPerms: Set[String] = rank.rankCfg("freeop", cfg)

    override def playerEv[Event <: Cancellable](event: Event, player: Player): Unit = {
      event match {
        case e: BlockBreakEvent => protect(e, player)
        case e: BlockPlaceEvent => protect(e, player)
        case e: PlayerInteractEvent => protect(e, player)
        case e: PlayerInteractEntityEvent => protect(e, player)
        case e: PlayerMoveEvent => jumppad(e)
        case e: VehicleMoveEvent => jumppad (e)
        case _ => ()
      }
    }

    override def join(player: Player): Unit = {
      val spawn = player.getServer.getWorld(cfg.getString("world.default"))
      player.teleport(spawn.getSpawnLocation)
    }
  }
}
