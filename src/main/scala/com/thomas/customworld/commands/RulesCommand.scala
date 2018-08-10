package scala.com.thomas.customworld.commands

import scala.com.thomas.customworld.messaging.{ConfigMsg, InfoMsg}
import org.bukkit.command.{Command, CommandExecutor, CommandSender}

class RulesCommand () extends CommandExecutor {
  override def onCommand(sender: CommandSender, command: Command, label: String, args: Array[String]): Boolean = {
    InfoMsg(ConfigMsg("rules")) sendClient sender
    true
  }
}
