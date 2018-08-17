package scala.com.thomas.customworld.event

import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.plugin.Plugin

trait EventModule {
  def join (player: Player): Unit = {}
  def leave (player: Player): Unit = {}
  def reset (player: Player): Unit = {}
  def ev (event: Event): Unit = {}
  def playerEv (event: Event, player:Player): Unit = {}
  def enable (plugin:Plugin): Unit = {}
  def disable (plugin:Plugin): Unit = {}
}
