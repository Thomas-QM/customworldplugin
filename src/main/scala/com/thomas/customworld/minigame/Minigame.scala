package com.thomas.customworld.minigame

import java.io.File
import java.util
import java.util.UUID

import com.thomas.customworld.{freeop, player}
import com.thomas.customworld
import com.thomas.customworld.util._
import org.bukkit.{ChatColor, GameMode, Location}
import org.bukkit.entity.{Entity, Player}
import org.bukkit.inventory.{Inventory, ItemStack}
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import com.boydti.fawe
import com.boydti.fawe.FaweAPI
import com.boydti.fawe.regions.general.CuboidRegionFilter
import com.boydti.fawe.regions.general.plot.PlotSquaredFeature
import com.boydti.fawe.util.EditSessionBuilder
import com.sk89q.worldedit
import com.sk89q.worldedit.{BlockVector, EditSession}
import com.sk89q.worldedit.blocks.BaseBlock
import com.sk89q.worldedit.extent.Extent
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat
import com.sk89q.worldedit.regions.CuboidRegion
import com.thomas.customworld.messaging.ErrorMsg
import org.bukkit.event.Event
import org.bukkit.event.block.{BlockBreakEvent, BlockPlaceEvent}
import org.bukkit.event.entity.{EntityDamageEvent, PlayerDeathEvent}
import org.bukkit.event.player.{PlayerEvent, PlayerItemDamageEvent, PlayerMoveEvent, PlayerTeleportEvent}

import scala.collection.mutable
import scala.collection.mutable.HashMap
import scala.reflect.ClassTag

abstract class Minigame[T <: MinigamePlayer : ClassTag](plugin: Plugin, region:Box, spawnLoc: Location, signLoc: Location) extends BukkitRunnable {
  val minPlayers = 2
  val gameTime = 60
  var state:GameState = WaitingForPlayers ()
  val name = "MINIGAME"

  def defaultPlayer (inventory:Array[ItemStack]): T
  var players: mutable.HashMap[UUID, T] = mutable.HashMap()

  val dim: worldedit.Vector = CageSchematic.getClipboard.getDimensions.divide(2)
  val cageRegion = Box(region.bworld, WEVec(spawnLoc.toVector).subtract(dim).toBlockVector, WEVec(spawnLoc.toVector).add(dim).toBlockVector)

  initializeMap()

  freeop.registerProtected(region)

  def getPlayers: Array[Player] = players map {case (x,y) => plugin.getServer.getPlayer(x)} toArray
  def getMinigamePlayers: Array[T] = (players map { case (_, y) => y }) toArray
  def mapPlayers (x: T => Unit) : Unit = players foreach { case (_, y:T) => x(y) }
  def mapPlayer (u: UUID, x: T => Unit): Unit = x(players(u))
  def doPlayers (x: Player => Unit): Unit = getPlayers foreach x
  def doMinigamePlayers (x: Player => T => Unit): Unit = players foreach {case (u,m) => x(plugin.getServer.getPlayer(u))(m)}

  def renderScoreboard (): Unit = {
    val playersarr = getPlayers
    if (!playersarr.isEmpty) {
      new SimpleScoreboard(name)
        .blank()
        .addMultiple(state.toString split "\\r?\\n")
        .blank()
        .add(ChatColor.RED + "Players:")
        .addMultiple(playersarr map (_.getName))
        .build()
        .send(playersarr)
    }
  }

  def initializeMap(): Unit = {
    cageRegion paste (CageSchematic, false)
  }

  def start (): Unit = {
    cageRegion fillBox 0

    mapPlayers (_.playing=true)
  }

  def startCountdown(): Unit = { }

  def end(): Unit = { initializeMap() }

  def tickPlaying (timeLeft:Int) :Boolean = {
    val left = getMinigamePlayers count (_.playing)
    if (left <= 1 || timeLeft <= 0) { true } else false
  }

  def updateState (state:GameState) :GameState = {
    renderScoreboard ()
    state match {
      case WaitingForPlayers () =>
        if (players.size >= minPlayers) {
          startCountdown()
          Countdown(5)
        } else state
      case Countdown(timeleft:Int) if timeleft <= 0 =>
        doPlayers (_.sendTitle("GO!", "", 0, 20, 40))
        start()
        Playing(gameTime)
      case Countdown(timeleft:Int) =>
        doPlayers (_.sendTitle(s"$timeleft", "left before game starts", 0, 20, 0))
        Countdown(timeleft-1)
      case Playing (timeLeft:Int) =>
        if (tickPlaying(timeLeft)) {
          end()

          doPlayers (leave)

          WaitingForPlayers ()
        } else Playing(timeLeft-1)
    }
  }

  override def run(): Unit = {
    state = updateState(state)
  }

  def join (newplayer: Player): Unit = {
    players += (newplayer.getUniqueId -> defaultPlayer (newplayer.getInventory.getContents))
    player.joinMinigames (newplayer)

    newplayer.setGameMode(GameMode.ADVENTURE)
    newplayer.getInventory.clear()

    newplayer.teleport(spawnLoc)
  }

  def spectate (player: Player): Unit = {
    mapPlayer (player.getUniqueId, _.playing = false)

    player.setAllowFlight(true)
    player.setFlying(true)
    player.teleport(spawnLoc)
  }

  def leave (leaveplayer: Player): Unit = {
    leaveplayer.getInventory.setContents(players(leaveplayer.getUniqueId).inventory)

    leaveplayer.setScoreboard(plugin.getServer.getScoreboardManager.getNewScoreboard)

    players -= leaveplayer.getUniqueId

    leaveplayer.teleport(signLoc)
  }

  def tryJoin (player: Player, loc:Location): Unit = {
    state match {
      case _:Playing => ()
      case _ if loc==signLoc => join(player)
    }
  }

  def tryLeave (player:Player): Unit = if (players contains player.getUniqueId) leave(player)

  def playerInGame (player:Entity): Boolean = {
    players contains player.getUniqueId
  }

  def playerMove (event: PlayerMoveEvent): Unit = {
    if (!(region hasLoc event.getTo)) {
      ErrorMsg("leavegame") sendClient event.getPlayer
      if (players(event.getPlayer.getUniqueId).playing) { event.setTo(event.getFrom) } else { event.setTo(spawnLoc) }
    }
  }

  def playerDamage(event: EntityDamageEvent, player: Player): Unit = { event.setCancelled(true) }
  def playerItemDamage(event: PlayerItemDamageEvent): Unit = { event.setCancelled(true) }

  def blockBreak (event: BlockBreakEvent): Unit = { event.setCancelled(true) }
  def blockPlace (event: BlockPlaceEvent): Unit = { event.setCancelled(true) }

  def tryOtherEv: Event => Unit = _ => ()
  def tryEv (event: Event): Unit = {
    event match {
      case e: PlayerEvent if playerInGame(e.getPlayer) =>
        e match {
          case e: PlayerMoveEvent => playerMove(e)
          //case e: PlayerTeleportEvent => playerMove(e)
          case e: PlayerItemDamageEvent => playerItemDamage(e)
          case _ => ()
        }

      case e: PlayerMoveEvent if region hasLoc e.getTo =>
        e.setCancelled(!e.getPlayer.hasPermission("spawnbuild"))
      case e: EntityDamageEvent => e.getEntity match {case x:Player if playerInGame(x) => playerDamage(e, x) case _ => ()}

      case e: BlockBreakEvent if playerInGame(e.getPlayer) => blockBreak(e)
      case e: BlockPlaceEvent if playerInGame(e.getPlayer)  => blockPlace(e)

      case _ => tryOtherEv(event)
    }
  }
}